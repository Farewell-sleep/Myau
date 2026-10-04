
package myau.ui;

import java.awt.Color;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.lwjgl.input.Mouse;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import myau.OpenMyau;
import myau.module.Module;
import myau.module.modules.AimAssist;
import myau.module.modules.AntiAFK;
import myau.module.modules.AntiDebuff;
import myau.module.modules.AntiFireball;
import myau.module.modules.AntiObbyTrap;
import myau.module.modules.AntiObfuscate;
import myau.module.modules.AntiVoid;
import myau.module.modules.AutoAnduril;
import myau.module.modules.AutoBlockIn;
import myau.module.modules.AutoClicker;
import myau.module.modules.AutoHeal;
import myau.module.modules.AutoTool;
import myau.module.modules.BedESP;
import myau.module.modules.BedNuker;
import myau.module.modules.BedTracker;
import myau.module.modules.Blink;
import myau.module.modules.Chams;
import myau.module.modules.ChestESP;
import myau.module.modules.ChestStealer;
import myau.module.modules.ESP;
import myau.module.modules.Eagle;
import myau.module.modules.FastPlace;
import myau.module.modules.Fly;
import myau.module.modules.Freeze;
import myau.module.modules.FullBright;
import myau.module.modules.GhostHand;
import myau.module.modules.GuiModule;
import myau.module.modules.HUD;
import myau.module.modules.HitBox;
import myau.module.modules.HitSelect;
import myau.module.modules.Disabler;
import myau.module.modules.SmartAttack;
import myau.module.modules.Indicators;
import myau.module.modules.InvManager;
import myau.module.modules.InvWalk;
import myau.module.modules.InventoryClicker;
import myau.module.modules.ItemESP;
import myau.module.modules.Jesus;
import myau.module.modules.KeepSprint;
import myau.module.modules.KillAura;
import myau.module.modules.Keybinds;
import myau.module.modules.LagRange;
import myau.module.modules.LightningTracker;
import myau.module.modules.LongJump;
import myau.module.modules.MCF;
import myau.module.modules.MoreKB;
import myau.module.modules.NameTags;
import myau.module.modules.NewScaffold;
import myau.module.modules.NickHider;
import myau.module.modules.NoFall;
import myau.module.modules.NoHitDelay;
import myau.module.modules.NoHurtCam;
import myau.module.modules.NoJumpDelay;
import myau.module.modules.NoRotate;
import myau.module.modules.PotionHUD;
import myau.module.modules.NoSlow;
import myau.module.modules.Radar;
import myau.module.modules.Reach;
import myau.module.modules.Refill;
import myau.module.modules.SafeWalk;
import myau.module.modules.Scaffold;
import myau.module.modules.Spammer;
import myau.module.modules.Speed;
import myau.module.modules.SpeedMine;
import myau.module.modules.Sprint;
import myau.module.modules.TargetHUD;
import myau.module.modules.TargetStrafe;
import myau.module.modules.Telly;
import myau.module.modules.Tracers;
import myau.module.modules.Trajectories;
import myau.module.modules.Velocity;
import myau.module.modules.ViewClip;
import myau.module.modules.Watermark;
import myau.module.modules.Wtap;
import myau.module.modules.Xray;
import myau.ui.components.CategoryComponent;
import net.minecraft.client.gui.GuiScreen;

public class ClickGui extends GuiScreen {
    private static ClickGui instance;
    private final File configFile = new File("./config/OpenMyau/", "clickgui.txt");
    private final ArrayList<CategoryComponent> categoryList;

    public ClickGui() {
        instance = this;

        List<Module> combatModules = new ArrayList<>();
        combatModules.add(OpenMyau.moduleManager.getModule(AimAssist.class));
        combatModules.add(OpenMyau.moduleManager.getModule(AutoClicker.class));
        combatModules.add(OpenMyau.moduleManager.getModule(KillAura.class));
        combatModules.add(OpenMyau.moduleManager.getModule(Wtap.class));
        combatModules.add(OpenMyau.moduleManager.getModule(Velocity.class));
        combatModules.add(OpenMyau.moduleManager.getModule(Freeze.class));
        combatModules.add(OpenMyau.moduleManager.getModule(Reach.class));
        combatModules.add(OpenMyau.moduleManager.getModule(TargetStrafe.class));
        combatModules.add(OpenMyau.moduleManager.getModule(NoHitDelay.class));
        combatModules.add(OpenMyau.moduleManager.getModule(AntiFireball.class));
        combatModules.add(OpenMyau.moduleManager.getModule(LagRange.class));
        combatModules.add(OpenMyau.moduleManager.getModule(HitBox.class));
        combatModules.add(OpenMyau.moduleManager.getModule(MoreKB.class));
        combatModules.add(OpenMyau.moduleManager.getModule(Refill.class));
        combatModules.add(OpenMyau.moduleManager.getModule(HitSelect.class));
        combatModules.add(OpenMyau.moduleManager.getModule(SmartAttack.class));

        List<Module> movementModules = new ArrayList<>();
        movementModules.add(OpenMyau.moduleManager.getModule(AntiAFK.class));
        movementModules.add(OpenMyau.moduleManager.getModule(Fly.class));
        movementModules.add(OpenMyau.moduleManager.getModule(Speed.class));
        movementModules.add(OpenMyau.moduleManager.getModule(LongJump.class));
        movementModules.add(OpenMyau.moduleManager.getModule(Sprint.class));
        movementModules.add(OpenMyau.moduleManager.getModule(SafeWalk.class));
        movementModules.add(OpenMyau.moduleManager.getModule(Jesus.class));
        movementModules.add(OpenMyau.moduleManager.getModule(Blink.class));
        movementModules.add(OpenMyau.moduleManager.getModule(NoFall.class));
        movementModules.add(OpenMyau.moduleManager.getModule(NoSlow.class));
        movementModules.add(OpenMyau.moduleManager.getModule(KeepSprint.class));
        movementModules.add(OpenMyau.moduleManager.getModule(Eagle.class));
        movementModules.add(OpenMyau.moduleManager.getModule(NoJumpDelay.class));
        movementModules.add(OpenMyau.moduleManager.getModule(AntiVoid.class));

        List<Module> renderModules = new ArrayList<>();
        renderModules.add(OpenMyau.moduleManager.getModule(ESP.class));
        renderModules.add(OpenMyau.moduleManager.getModule(Chams.class));
        renderModules.add(OpenMyau.moduleManager.getModule(FullBright.class));
        renderModules.add(OpenMyau.moduleManager.getModule(Tracers.class));
        renderModules.add(OpenMyau.moduleManager.getModule(NameTags.class));
        renderModules.add(OpenMyau.moduleManager.getModule(Xray.class));
        renderModules.add(OpenMyau.moduleManager.getModule(BedESP.class));
        renderModules.add(OpenMyau.moduleManager.getModule(ItemESP.class));
        renderModules.add(OpenMyau.moduleManager.getModule(ViewClip.class));
        renderModules.add(OpenMyau.moduleManager.getModule(NoHurtCam.class));
        renderModules.add(OpenMyau.moduleManager.getModule(GuiModule.class));
        renderModules.add(OpenMyau.moduleManager.getModule(ChestESP.class));
        renderModules.add(OpenMyau.moduleManager.getModule(Trajectories.class));
        renderModules.add(OpenMyau.moduleManager.getModule(Radar.class));

        List<Module> hudModules = new ArrayList<>();
        hudModules.add(OpenMyau.moduleManager.getModule(HUD.class));
        hudModules.add(OpenMyau.moduleManager.getModule(TargetHUD.class));
        hudModules.add(OpenMyau.moduleManager.getModule(Indicators.class));
        hudModules.add(OpenMyau.moduleManager.getModule(PotionHUD.class));
        hudModules.add(OpenMyau.moduleManager.getModule(Watermark.class));
        hudModules.add(OpenMyau.moduleManager.getModule(Keybinds.class));

        List<Module> playerModules = new ArrayList<>();
        playerModules.add(OpenMyau.moduleManager.getModule(AutoHeal.class));
        playerModules.add(OpenMyau.moduleManager.getModule(AutoTool.class));
        playerModules.add(OpenMyau.moduleManager.getModule(ChestStealer.class));
        playerModules.add(OpenMyau.moduleManager.getModule(InvManager.class));
        playerModules.add(OpenMyau.moduleManager.getModule(InvWalk.class));
        playerModules.add(OpenMyau.moduleManager.getModule(Scaffold.class));
        playerModules.add(OpenMyau.moduleManager.getModule(Telly.class));
        playerModules.add(OpenMyau.moduleManager.getModule(NewScaffold.class));
        playerModules.add(OpenMyau.moduleManager.getModule(AutoBlockIn.class));
        playerModules.add(OpenMyau.moduleManager.getModule(SpeedMine.class));
        playerModules.add(OpenMyau.moduleManager.getModule(FastPlace.class));
        playerModules.add(OpenMyau.moduleManager.getModule(GhostHand.class));
        playerModules.add(OpenMyau.moduleManager.getModule(MCF.class));
        playerModules.add(OpenMyau.moduleManager.getModule(AntiDebuff.class));

        List<Module> miscModules = new ArrayList<>();
        miscModules.add(OpenMyau.moduleManager.getModule(Spammer.class));
        miscModules.add(OpenMyau.moduleManager.getModule(BedNuker.class));
        miscModules.add(OpenMyau.moduleManager.getModule(BedTracker.class));
        miscModules.add(OpenMyau.moduleManager.getModule(LightningTracker.class));
        miscModules.add(OpenMyau.moduleManager.getModule(NoRotate.class));
        miscModules.add(OpenMyau.moduleManager.getModule(NickHider.class));
        miscModules.add(OpenMyau.moduleManager.getModule(AntiObbyTrap.class));
        miscModules.add(OpenMyau.moduleManager.getModule(AntiObfuscate.class));
        miscModules.add(OpenMyau.moduleManager.getModule(AutoAnduril.class));
        miscModules.add(OpenMyau.moduleManager.getModule(InventoryClicker.class));
        miscModules.add(OpenMyau.moduleManager.getModule(Disabler.class));

        Comparator<Module> comparator = Comparator.comparing(m -> m.getName().toLowerCase());
        combatModules.sort(comparator);
        movementModules.sort(comparator);
        renderModules.sort(comparator);
        hudModules.sort(comparator);
        playerModules.sort(comparator);
        miscModules.sort(comparator);

        Set<Module> registered = new HashSet<>();
        registered.addAll(combatModules);
        registered.addAll(movementModules);
        registered.addAll(renderModules);
        registered.addAll(hudModules);
        registered.addAll(playerModules);
        registered.addAll(miscModules);

        for (Module module : OpenMyau.moduleManager.modules.values()) {
            if (!registered.contains(module)) {
                throw new RuntimeException(module.getClass().getName() + " is unregistered to click gui.");
            }
        }

        this.categoryList = new ArrayList<>();
        int topOffset = 5;


        CategoryComponent combat = new CategoryComponent("Combat", combatModules);
        combat.setY(topOffset);
        categoryList.add(combat);
        topOffset += 20;

        CategoryComponent movement = new CategoryComponent("Movement", movementModules);
        movement.setY(topOffset);
        categoryList.add(movement);
        topOffset += 20;

        CategoryComponent render = new CategoryComponent("Render", renderModules);
        render.setY(topOffset);
        categoryList.add(render);
        topOffset += 20;

        CategoryComponent hud = new CategoryComponent("HUD", hudModules);
        hud.setY(topOffset);
        categoryList.add(hud);
        topOffset += 20;

        CategoryComponent player = new CategoryComponent("Player", playerModules);
        player.setY(topOffset);
        categoryList.add(player);
        topOffset += 20;

        CategoryComponent misc = new CategoryComponent("Misc", miscModules);
        misc.setY(topOffset);
        categoryList.add(misc);

        loadPositions();
    }

    public static ClickGui getInstance() {
        return instance;
    }

    public void initGui() {
        super.initGui();
    }

    public void drawScreen(int x, int y, float p) {
        drawRect(0, 0, this.width, this.height, new Color(0, 0, 0, 100).getRGB());

        mc.fontRendererObj.drawStringWithShadow("OpenMyau " + OpenMyau.version, 4, this.height - 3 - mc.fontRendererObj.FONT_HEIGHT * 3, new Color(60, 162, 253).getRGB());
        mc.fontRendererObj.drawStringWithShadow("dev, ksyz", 4, this.height - 3 - mc.fontRendererObj.FONT_HEIGHT * 2, new Color(60, 162, 253).getRGB());
        mc.fontRendererObj.drawStringWithShadow("TTHILLTT Forked", 4, this.height - 3 - mc.fontRendererObj.FONT_HEIGHT, new Color(60, 162, 253).getRGB());

        for (CategoryComponent category : categoryList) {
            category.render(this.fontRendererObj);
            category.handleDrag(x, y);

            for (Component module : category.getModules()) {
                module.update(x, y);
            }
        }

        int wheel = Mouse.getDWheel();
        if (wheel != 0) {
            int scrollDir = wheel > 0 ? 1 : -1;
            for (CategoryComponent category : categoryList) {
                category.onScroll(x, y, scrollDir);
            }
        }
    }

    public void mouseClicked(int x, int y, int mouseButton) {
        Iterator<CategoryComponent> btnCat = categoryList.iterator();
        while (true) {
            CategoryComponent category;
            do {
                do {
                    if (!btnCat.hasNext()) {
                        return;
                    }

                    category = btnCat.next();
                    if (category.insideArea(x, y) && !category.isHovered(x, y) && !category.mousePressed(x, y) && mouseButton == 0) {
                        category.mousePressed(true);
                        category.xx = x - category.getX();
                        category.yy = y - category.getY();
                    }

                    if (category.mousePressed(x, y) && mouseButton == 0) {
                        category.setOpened(!category.isOpened());
                    }

                    if (category.isHovered(x, y) && mouseButton == 0) {
                        category.setPin(!category.isPin());
                    }
                } while (!category.isOpened());
            } while (category.getModules().isEmpty());

            for (Component c : category.getModules()) {
                c.mouseDown(x, y, mouseButton);
            }
        }

    }

    public void mouseReleased(int x, int y, int mouseButton) {
        Iterator<CategoryComponent> iterator = categoryList.iterator();

        CategoryComponent categoryComponent;
        while (iterator.hasNext()) {
            categoryComponent = iterator.next();
            if (mouseButton == 0) {
                categoryComponent.mousePressed(false);
            }
        }

        iterator = categoryList.iterator();

        while (true) {
            do {
                do {
                    if (!iterator.hasNext()) {
                        return;
                    }

                    categoryComponent = iterator.next();
                } while (!categoryComponent.isOpened());
            } while (categoryComponent.getModules().isEmpty());

            for (Component component : categoryComponent.getModules()) {
                component.mouseReleased(x, y, mouseButton);
            }
        }
    }

    public void keyTyped(char typedChar, int key) {
        if (key == 1) {
            this.mc.displayGuiScreen(null);
        } else {
            Iterator<CategoryComponent> btnCat = categoryList.iterator();

            while (true) {
                CategoryComponent cat;
                do {
                    do {
                        if (!btnCat.hasNext()) {
                            return;
                        }

                        cat = btnCat.next();
                    } while (!cat.isOpened());
                } while (cat.getModules().isEmpty());

                for (Component component : cat.getModules()) {
                    component.keyTyped(typedChar, key);
                }
            }
        }
    }

    public void onGuiClosed() {
        savePositions();
    }

    public boolean doesGuiPauseGame() {
        return false;
    }

    private void savePositions() {
        JsonObject json = new JsonObject();
        for (CategoryComponent cat : categoryList) {
            JsonObject pos = new JsonObject();
            pos.addProperty("x", cat.getX());
            pos.addProperty("y", cat.getY());
            pos.addProperty("open", cat.isOpened());
            json.add(cat.getName(), pos);
        }
        try (FileWriter writer = new FileWriter(configFile)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(json, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadPositions() {
        if (!configFile.exists()) return;
        try (FileReader reader = new FileReader(configFile)) {
            JsonObject json = new JsonParser().parse(reader).getAsJsonObject();
            for (CategoryComponent cat : categoryList) {
                if (json.has(cat.getName())) {
                    JsonObject pos = json.getAsJsonObject(cat.getName());
                    cat.setX(pos.get("x").getAsInt());
                    cat.setY(pos.get("y").getAsInt());
                    cat.setOpened(pos.get("open").getAsBoolean());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
