#include "target.h"

#include <windows.h>
#include <tlhelp32.h>
#include <winternl.h>

#include <algorithm>
#include <cwctype>

namespace {

typedef NTSTATUS(NTAPI *QueryInformationProcess)(HANDLE, PROCESSINFOCLASS, PVOID,
                                                 ULONG, PULONG);

std::wstring lower(const std::wstring &text) {
    std::wstring out;
    out.reserve(text.size());
    for (wchar_t c : text) {
        out.push_back((wchar_t)towlower(c));
    }
    return out;
}

bool contains(const std::wstring &haystack, const wchar_t *needle) {
    return haystack.find(needle) != std::wstring::npos;
}

std::wstring commandLineOf(DWORD pid) {
    HANDLE process = OpenProcess(PROCESS_QUERY_INFORMATION | PROCESS_VM_READ, FALSE, pid);
    if (!process) {
        return L"";
    }
    std::wstring result;
    HMODULE ntdll = GetModuleHandleW(L"ntdll.dll");
    QueryInformationProcess query =
            ntdll ? (QueryInformationProcess)GetProcAddress(ntdll, "NtQueryInformationProcess")
                  : nullptr;
    if (!query) {
        CloseHandle(process);
        return L"";
    }
    PROCESS_BASIC_INFORMATION basic{};
    ULONG returned = 0;
    if (query(process, ProcessBasicInformation, &basic, sizeof(basic), &returned) == 0
            && basic.PebBaseAddress) {
        PEB peb{};
        SIZE_T read = 0;
        if (ReadProcessMemory(process, basic.PebBaseAddress, &peb, sizeof(peb), &read)) {
            RTL_USER_PROCESS_PARAMETERS parameters{};
            if (ReadProcessMemory(process, peb.ProcessParameters, &parameters,
                                  sizeof(parameters), &read)) {
                USHORT bytes = parameters.CommandLine.Length;
                if (bytes > 0 && bytes < 64 * 1024) {
                    std::wstring buffer(bytes / sizeof(wchar_t), 0);
                    if (ReadProcessMemory(process, parameters.CommandLine.Buffer, &buffer[0],
                                          bytes, &read)) {
                        result = buffer;
                    }
                }
            }
        }
    }
    CloseHandle(process);
    return result;
}
}
int scoreCommandLine(const std::wstring &rawCommandLine) {
    std::wstring cmd = lower(rawCommandLine);
    if (cmd.empty()) {
        return 0;
    }

    bool isGame = contains(cmd, L"net.minecraft.client.main.main")
            || contains(cmd, L"net.minecraft.launchwrapper.launch");
    if (!isGame
            && (contains(cmd, L"hmcl") || contains(cmd, L"multimc")
                || contains(cmd, L"prismlauncher") || contains(cmd, L"atlauncher")
                || contains(cmd, L"gdlauncher") || contains(cmd, L"org.gradle")
                || contains(cmd, L"gradle-launcher") || contains(cmd, L"myau")
                || contains(cmd, L"ovson"))) {
        return -100;
    }
    int score = 0;
    if (isGame) {
        score += 50;
    }
    if (contains(cmd, L"--gamedir")) score += 10;
    if (contains(cmd, L"--assetindex") || contains(cmd, L"--assetsdir")) score += 10;
    if (contains(cmd, L"--accesstoken")) score += 10;
    if (contains(cmd, L"--uuid")) score += 5;
    if (contains(cmd, L"--versiontype")) score += 5;
    if (contains(cmd, L"java.library.path") && contains(cmd, L"natives")) score += 10;
    if (contains(cmd, L"lunarclient") || contains(cmd, L".lunarclient")) score += 30;
    if (contains(cmd, L"badlion")) score += 30;
    return score;
}
std::wstring launcherOf(const std::wstring &rawCommandLine) {
    std::wstring cmd = lower(rawCommandLine);
    if (contains(cmd, L"lunarclient") || contains(cmd, L".lunarclient")) {
        return L"Lunar";
    }
    if (contains(cmd, L"badlion")) {
        return L"Badlion";
    }
    if (contains(cmd, L"net.minecraft.launchwrapper.launch")
            || contains(cmd, L"forge") || contains(cmd, L"fml")) {
        return L"Forge";
    }
    if (contains(cmd, L"net.minecraft.client.main.main")) {
        return L"Vanilla";
    }
    return L"Unknown";
}
std::vector<Candidate> findCandidates() {
    std::vector<Candidate> found;
    HANDLE snapshot = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
    if (snapshot == INVALID_HANDLE_VALUE) {
        return found;
    }
    PROCESSENTRY32W entry{};
    entry.dwSize = sizeof(entry);
    if (Process32FirstW(snapshot, &entry)) {
        do {
            std::wstring image = lower(entry.szExeFile);
            if (image != L"java.exe" && image != L"javaw.exe") {
                continue;
            }
            Candidate candidate;
            candidate.pid = entry.th32ProcessID;
            candidate.image = entry.szExeFile;
            candidate.commandLine = commandLineOf(entry.th32ProcessID);
            candidate.score = scoreCommandLine(candidate.commandLine);
            found.push_back(candidate);
        } while (Process32NextW(snapshot, &entry));
    }
    CloseHandle(snapshot);
    return found;
}
