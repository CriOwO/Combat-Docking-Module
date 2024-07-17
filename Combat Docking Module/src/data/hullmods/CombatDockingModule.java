package data.hullmods;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.PluginPick;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignPlugin;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAIPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI;
import com.fs.starfarer.api.combat.FighterWingAPI;

import data.scripts.ai.combat_docking_AI;

public class CombatDockingModule extends BaseHullMod {
	
	private static final Set<String> WhiteListed = new HashSet<>();
	
	private final float Repair = 100, CR = 20; //Hullmod in game description.
	private combat_docking_AI DockingAI;
	float HPPercent = 0.80f;
	float BaseTimer = 20f;
    float CRmarker = 0.4f;
    
    private float SupplyFuelReduc = 90f;

	private final IntervalUtil BASE_REFIT = new IntervalUtil(20f, 20f);

	private boolean carrier_available = false;
	
    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
    	
    	carrier_check ();
    	
    	if (carrier_available = true) {
    	stats.getSuppliesPerMonth().modifyMult(id, (100f - SupplyFuelReduc)/100f);
    	stats.getFuelUseMod().modifyMult(id, (100f - SupplyFuelReduc)/100f);
    	}
        
    }
    
    @Override
    public String getDescriptionParam(int index, HullSize hullSize) {
        if (index == 0) {
            return "" + (int) Repair + "%";
        }
        if (index == 1) {
            return "" + (int) CR + "%";
        }
        if (index == 2) {
            return "" + (int) SupplyFuelReduc + "%";
        }
        return null;
    }
    
    @SuppressWarnings("deprecation")
	@Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
    	
        ship.setAffectedByNebula(false);
        //ship.isFighter();
        //ship.isSelectableInWarroom();
        ship.setCollisionClass(CollisionClass.FIGHTER);

    }
    
    public void advanceInCombat(ShipAPI ship, float amount) {
    	
    	if (ship.getCollisionClass() == CollisionClass.SHIP) {
    		ship.setCollisionClass(CollisionClass.FIGHTER);
    		ship.setAffectedByNebula(false);
    	}
    	
        if (!ship.isAlive()) return;
		MutableShipStatsAPI stats = ship.getMutableStats();
		ShipAPI playerShip = Global.getCombatEngine().getPlayerShip();
		DockingAI = new combat_docking_AI(ship);
		
	    for (ShipAPI carrier : CombatUtils.getShipsWithinRange(ship.getLocation(), 8000.0F)) { //Checks for carrier within range.
	        if (carrier.getOwner() != ship.getOwner() || 
	        		carrier.getVariant() == null || carrier.isPhased() || carrier.getNumFighterBays() == 0 || carrier == ship || carrier.isFighter() || carrier.isFrigate()) {
	          continue;
	        }
	        if (carrier.getOwner() == ship.getOwner() && carrier != ship) {
	        	if (ship == playerShip) //If it is the player, display the text.
	        		Global.getCombatEngine().maintainStatusForPlayerShip("AceSystem0", "graphics/ui/icons/icon_retreat2.png","CARRIER", carrier.getName() ,false);
	        }
	        
    	    for (ShipAPI fighterAI : CombatUtils.getShipsWithinRange(ship.getLocation(), 4000.0F)) { //Check for fighters nearby to use their landing bay.
    	    	
    	    if (fighterAI.getOwner() != ship.getOwner() || !fighterAI.isFighter() || fighterAI.getWing() == null) {
    	          continue;
    	        }
    	    
    	    if (fighterAI.getWing().getSource() == ship) {continue;} //skip if the source is self
    	    
				/*
				 * if (fighterAI.getOwner() == ship.getOwner() &&
				 * fighterAI.getWing().getSourceShip() != playerShip) { if (ship == playerShip
				 * && fighterAI.getWing() != null) //If it is the player, display the text.
				 * Global.getCombatEngine().maintainStatusForPlayerShip("AceSystem4",
				 * "graphics/ui/icons/icon_retreat2.png","RALLY POINT",
				 * fighterAI.getWing().getSourceShip().getName() ,false); }
				 */
    	    }
	        
			float CurrentHull = ship.getHitpoints();
			float MaxHull = ship.getMaxHitpoints();
			float CurrentCR = ship.getCurrentCR();

				if ((CurrentHull <= MaxHull * HPPercent) || (CurrentCR < CRmarker)){
					
					if (ship == playerShip) 
						Global.getCombatEngine().maintainStatusForPlayerShip("AceSystem1", "graphics/ui/icons/icon_repair_refit.png","SYSTEMS CRITICAL", "WARNING" ,false);
					
					if (ship != playerShip) {
						
						ship.setShipAI(DockingAI);
						DockingAI.init();
						DockingAI.needsRefit();
						DockingAI.beginlandingAI();
						BASE_REFIT.advance(amount);
			    		if (BASE_REFIT.intervalElapsed()) {
			    		DockingAI.advance(BaseTimer);
			    		}
					}
					
					}
				
				boolean hasMissileSlots = false; //Missile ammo check
				boolean hasMissileLeft = false;

				for (WeaponAPI weapon : ship.getAllWeapons()) {
					if (weapon.getType() == WeaponAPI.WeaponType.MISSILE) {
						hasMissileSlots = true;
						if (weapon.getAmmo() != 0) {
							hasMissileLeft = true;
							break;
						}
					}
				}
				

				if (hasMissileSlots && !hasMissileLeft) {
					
					if (ship == playerShip) 
						Global.getCombatEngine().maintainStatusForPlayerShip("AceSystem1", "graphics/ui/icons/icon_repair_refit.png","NO MUNITIONS", "WARNING" ,false);
					
					if (ship != playerShip) {
						
						ship.setShipAI(DockingAI);
						DockingAI.init();
						DockingAI.needsRefit();
						DockingAI.beginlandingAI();
						BASE_REFIT.advance(amount);
			    		if (BASE_REFIT.intervalElapsed()) {
			    		DockingAI.advance(BaseTimer);
			    		}
					}
				}
				
	      }
	    
	}
    
    static {
    	loadWhiteListedShips("cdm_whitelist.csv");
    }
    
    public static void loadWhiteListedShips(String path) { //Allows ship IDs that are whitelisted.
        try {
          JSONArray cdm_whitelistCsv = Global.getSettings().getMergedSpreadsheetDataForMod("id", path, "combat_docking_module");
          for (int x = 0; x < cdm_whitelistCsv.length(); x++) {
            
            String shipId = "<unknown>";
            try {
              JSONObject row = cdm_whitelistCsv.getJSONObject(x);
              shipId = row.getString("id");
              if (!shipId.isEmpty()) {

                WhiteListed.add(shipId);
                
              } 
            } catch (JSONException ex) {} 
          } 
        } catch (IOException|JSONException ex) {} 
        catch (RuntimeException runtimeException) {}
      }
    
    public void carrier_check () {
    	   	
    	CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
    	
    	  if (fleet != null) {
    	    	
    		  for (FleetMemberAPI member: Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
        			if (member.isCarrier()) {
        			carrier_available  = true;
        			}
        			else {continue;}
        			}            
    	  }
    }
    
    @Override
    public boolean isApplicableToShip(ShipAPI ship)
    {
        for (String tmp : WhiteListed) {
            if (ship.getHullSpec().getHullId().contains(tmp)) {                
                return true;
            }
        }
    	
        // Allows any ship with a dockable designations
        return ( ship.getHullSpec().getDesignation().contains("Shuttle") || 
        		ship.getHullSpec().getDesignation().contains("Courier") || 
        		ship.getHullSpec().getDesignation().contains("Drone") ||
        		ship.getHullSpec().getDesignation().contains("Scout") ||
        		ship.getHullSpec().getDesignation().contains("Corvette") ||
        		ship.getHullSpec().getDesignation().contains("Light Rig") ||
        		ship.getHullSpec().getDesignation().contains("Pinnace"));
        
    }
	 
}
