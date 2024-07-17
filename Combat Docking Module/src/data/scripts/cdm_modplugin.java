package data.scripts;

import java.util.HashMap;
import java.util.Map;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.PluginPick;
import com.fs.starfarer.api.combat.ShipAIPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.util.Misc;

import data.scripts.ai.combat_docking_AI;

import com.fs.starfarer.api.campaign.CampaignPlugin;
import com.fs.starfarer.api.campaign.SectorEntityToken;

public class cdm_modplugin
  extends BaseModPlugin{

	public PluginPick<ShipAIPlugin> pickShipAI(FleetMemberAPI member, ShipAPI ship) {	    
		
	    if (ship.getVariant().hasHullMod("combat_docking_module")) {
	    	  return new PluginPick<ShipAIPlugin>(new combat_docking_AI(ship), CampaignPlugin.PickPriority.MOD_GENERAL);
	    } 
	    return super.pickShipAI(member, ship);
	  }
}