package me.xemor.superheroes;

import me.xemor.superheroes.data.HeroHandler;
import me.xemor.superheroes.events.PlayerChangedSuperheroEvent;
import me.xemor.superheroes.skills.Skill;
import me.xemor.superheroes.skills.skilldata.CraftingData;
import me.xemor.superheroes.skills.skilldata.SkillData;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

public class RecipeHandler implements Listener {

    private final HashMap<NamespacedKey, Superhero> recipeToPower = new HashMap<>();
    private final HeroHandler heroHandler;


    public RecipeHandler(HeroHandler heroHandler) {
        this.heroHandler = heroHandler;
    }

    @EventHandler
    public void prepareCrafting(PrepareItemCraftEvent e) {
        Superhero power = null;
        Recipe eventRecipe = e.getRecipe();
        if (eventRecipe instanceof Keyed) {
            power = recipeToPower.get(((Keyed)eventRecipe).getKey());
        }
        if (power == null) {
            return;
        }
        e.getInventory().setResult(new ItemStack(Material.AIR));
        List<HumanEntity> viewers = e.getViewers();
        for (HumanEntity humanEntity : viewers) {
            if (humanEntity instanceof Player player) {
                if (power.equals(heroHandler.getSuperhero(player))) {
                    e.getInventory().setResult(eventRecipe.getResult());
                }
            }
        }
    }

    @EventHandler
    public void onPowerGain(PlayerChangedSuperheroEvent e) {
        Collection<NamespacedKey> recipeKeys = getRecipesFromSuperhero(e.getNewHero());
        e.getPlayer().discoverRecipes(recipeKeys);
    }

    public Collection<NamespacedKey> getRecipesFromSuperhero(Superhero superhero) {
        Collection<SkillData> skillDatas = superhero.getSkillData(Skill.getSkill("CRAFTING"));
        List<NamespacedKey> recipes = new ArrayList<>();
        for (SkillData skillData : skillDatas) {
            CraftingData craftingData = (CraftingData) skillData;
            recipes.add(((Keyed) craftingData.getRecipe()).getKey());
        }
        return recipes;
    }

    @EventHandler
    public void onPowerLost(PlayerChangedSuperheroEvent e) {
        Collection<NamespacedKey> recipeKeys = getRecipesFromSuperhero(e.getOldHero());
        e.getPlayer().undiscoverRecipes(recipeKeys);
    }
}
