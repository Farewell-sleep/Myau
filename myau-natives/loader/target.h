#pragma once

#include <windows.h>

#include <string>
#include <vector>

struct Candidate {
    DWORD pid = 0;
    std::wstring image;
    std::wstring commandLine;
    int score = 0;
};

std::vector<Candidate> findCandidates();

int scoreCommandLine(const std::wstring &rawCommandLine);

std::wstring launcherOf(const std::wstring &rawCommandLine);
