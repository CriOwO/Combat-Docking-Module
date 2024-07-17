package data.scripts.ai;

import data.scripts.util.StolenUtils;

import java.awt.Color;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import org.lazywizard.lazylib.CollisionUtils;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.Point;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SoundAPI;
import com.fs.starfarer.api.combat.ArmorGridAPI;
import com.fs.starfarer.api.combat.CombatAssignmentType;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI;
import com.fs.starfarer.api.combat.FighterLaunchBayAPI;
import com.fs.starfarer.api.combat.FighterWingAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShieldAPI;
import com.fs.starfarer.api.combat.ShipAIConfig;
import com.fs.starfarer.api.combat.ShipAIPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.combat.CombatEngine;
import com.fs.starfarer.combat.entities.Ship;
import com.fs.starfarer.api.combat.CombatTaskManagerAPI;

//based on code from Sundog's ICE repair drone and Dark.Revenant's Imperium Titan. Copied Maltese AI

public class combat_docking_AI extends BaseShipAI {
	private final ShipwideAIFlags flags = new ShipwideAIFlags();
    private final ShipAIConfig config = new ShipAIConfig();
    
    private ShipAPI carrier;
    private ShipAPI fighterbay;
    private CombatEngineAPI engine;
    private CombatFleetManagerAPI combatfleet;
    private CombatFleetManagerAPI fleetManager;
    ShipAPI fightercopy;
    private List<FighterLaunchBayAPI> carrierdata;
    private boolean carrierworking;
    ShipAPI target;
    Vector2f targetOffset;
    Point cellToFix = new Point();
    Random rng = new Random();
    ArmorGridAPI armorGrid;
    SoundAPI repairSound;
    float max, cellSize;
    int gridWidth, gridHeight, cellCount;
    boolean returning = false;
    boolean spark = false;
    float dontRestoreAmmoUntil = 0;
    float targetFacingOffset = Float.MIN_VALUE;
    float range = 4000f;
    boolean needrepair = false;
    float HPPercent = 0.90f;
    float CRmarker = 0.4f;
    float CRmax = 1f;
    
    
    
    private final IntervalUtil interval = new IntervalUtil(0.25f, 0.33f);
    private final IntervalUtil countdown = new IntervalUtil(4f, 4f);
    private final IntervalUtil BASE_REFIT = new IntervalUtil(15f, 15f);

    
    Vector2f getDestination() {
        return new Vector2f();
    }

    public combat_docking_AI(ShipAPI ship) {
		super(ship);		
	}

    @Override
    public void advance(float amount) {
    	
    	if(carrier == null) {
    		init();
    		//if(carrier == null){
    			//delete
    		//}
    	}
    	
    	ShipAPI playerShip = Global.getCombatEngine().getPlayerShip();
    	if (ship == playerShip) 
			Global.getCombatEngine().maintainStatusForPlayerShip("AceSystem3", "graphics/ui/icons/icon_repair_refit.png", "WANZER AUTOPILOT" , "INITIALIZED" ,false);
    	
    	
    	if(ship.isLanding() && ship.isFighter()) {
    		//doingMx = false; //stop the repair movement
    		countdown.advance(amount);
    		if (countdown.intervalElapsed()) {
    			ship.getWing().getSource().land(ship);
    			return;
    		}
    	}
    	
    	if(ship.isLanding()) {
    		//doingMx = false; //stop the repair movement
    		
    		ship.setShipSystemDisabled(true);
    		ship.setControlsLocked(true);
    			
    		countdown.advance(amount);
    		if (countdown.intervalElapsed()) {
    			 ship.setControlsLocked(true);
    			 
    			 if(fighterbay!=null) {
    	    			StolenUtils.setLocation(ship, fighterbay.getWing().getSource().getLandingLocation(fighterbay)); //set to location of carrier in case of drift
    	    		}
    			 
     	    	if(carrier == null || !carrier.isAlive()) { //Destroy ship if carrier is destroyed during landing
        	    StolenUtils.destroy(ship);
        	    return; }
    			 
    		}
    	}
    	
    	if(ship.isFinishedLanding() && !ship.isFighter()) {
    		//doingMx = false; //stop the repair movement
    		((Ship) carrier).isReplacingFighters();
    		carrierworking = ((Ship) carrier).isReplacingFighters(); //graphic effects for fighter bay
    		
    		for (ShipAPI modules : ship.getChildModulesCopy()) { //Modules also is finished landing.
    			if (modules == null) continue;
    			modules.isFinishedLanding();
    			}
    		
    		if(carrier == null || !carrier.isAlive()) { //Destroy ship if carrier is destroyed during landing
    		StolenUtils.destroy(ship);
   		 	return; }
    		
    		for (FleetMemberAPI member : fleetManager.getRetreatedCopy()) { //Check if the carrier retreats during landing
    			if(fleetManager.getShipFor(member)!=carrier) {
    				continue;
    			}
    			if(fleetManager.getShipFor(member)==carrier) {
    				fleetManager.addToReserves(ship.getFleetMember());
    				engine.removeEntity(ship);
    			}
    			
    		}
    		
    		BASE_REFIT.advance(amount);
    		if (BASE_REFIT.intervalElapsed()) {
    		ship.setHitpoints(ship.getMaxHitpoints()); //Hull to 100%
    		ship.getFluxTracker().stopOverload();
    		ship.getFluxTracker().setCurrFlux(0f);
    		StolenUtils.setArmorPercentage(ship, 100f); //Armor to 100%
    		
    		if (fighterbay!=null) {
    			StolenUtils.setLocation(ship, fighterbay.getWing().getSource().getLandingLocation(fighterbay)); //set to location of carrier in case of drift
    		}
    		
    		if ((ship.getCurrentCR() <= CRmarker)) { 

    			float CRRefit = Math.min(ship.getCurrentCR() + 0.15f, CRmax); //Add 15 CR up to the maximum 
    			ship.setCurrentCR(CRRefit);
    		}
    		
    		ship.clearDamageDecals();
    		
    		 List<WeaponAPI> weapons = ship.getAllWeapons();
    		 for (WeaponAPI w : weapons) {
    			if (w.usesAmmo()) w.resetAmmo();
    		 	}
    		 
    		((Ship) ship).setAnimatedLaunch();
    		ship.setControlsLocked(false);
    		ship.setShipSystemDisabled(false);
    		carrierworking = false;
    		returning = false;
    		needrepair = false;
    		ship.resetDefaultAI();
    		
    		for (ShipAPI modules : ship.getChildModulesCopy()) {
        		if (modules == null) continue;
        		modules.setHitpoints(ship.getMaxHitpoints());
        		modules.getFluxTracker().stopOverload();
        		modules.getFluxTracker().setCurrFlux(0f);
        		modules.clearDamageDecals();
        		StolenUtils.setArmorPercentage(ship, 100f); //Armor to 100%
        		((Ship) modules).setAnimatedLaunch();
        		}
    		
    		}
    	}
    		
    	
    	
    	interval.advance(amount);
        if (interval.intervalElapsed()) {
        	super.advance(amount);
            
            if(target == null) return;
            
            else if(returning && !ship.isLanding() && MathUtils.getDistance(ship, carrier) < carrier.getCollisionRadius()/3f) {
            	ship.setShipSystemDisabled(true);
            	ship.beginLandingAnimation(carrier);
            	for (ShipAPI modules : ship.getChildModulesCopy()) {
            		if (modules == null) continue;
            		modules.beginLandingAnimation(carrier);
            	}
            	
            }
            
        }
        goToDestination();
        
    }
    
    public void beginlandingAI() {
    	
    	if(carrier == null) return;
    	
        if(returning && !ship.isLanding() && MathUtils.getDistance(ship, carrier) < carrier.getCollisionRadius()/3f) {
        	ship.beginLandingAnimation(carrier);
        	for (ShipAPI modules : ship.getChildModulesCopy()) {
        		if (modules == null) continue;
        		modules.beginLandingAnimation(carrier);
        	}
        	
        }
    	
    }
    
    @Override
    public boolean needsRefit() {
    	float CurrentHull = ship.getHitpoints();
		float MaxHull = ship.getMaxHitpoints();
		float CurrentCR = ship.getCurrentCR();

			if ((CurrentHull < MaxHull * HPPercent) || (CurrentCR < CRmarker)){
				needrepair = true;
				returning = true;
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
				needrepair = true;
				returning = true;
			}
			
			
        return needrepair;
    }

    @Override
    public void cancelCurrentManeuver() {
    }

    @Override
    public void evaluateCircumstances() {
		/*
		 * if(carrier == null || !carrier.isAlive()) { StolenUtils.destroy(ship);
		 * return; }
		 */

        setTarget(chooseTarget());

        if(returning && ship.isFighter()) {
            targetOffset = StolenUtils.toRelative(target, ship.getWing().getSource().getLandingLocation(ship));
        } 
        if(returning) {
            targetOffset = StolenUtils.toRelative(target, carrier.getLocation());
        }
        
        if(returning&&fighterbay!=null) {
            targetOffset = StolenUtils.toRelative(target, fighterbay.getWing().getSource().getLandingLocation(fighterbay));
        }
    }
    
    ShipAPI chooseTarget() {
    	if(carrier == null) {
    		return ship; // Targets shelf if no carrier nearby to prevent null exception.
    	}
    	
        if(needsRefit()) {
            returning = true;
            //ship.getFluxTracker().setCurrFlux(ship.getFluxTracker().getMaxFlux());
            return carrier;
        } else returning = false;
        
        if(carrier.getShipTarget() != null
                && carrier.getOwner() == carrier.getShipTarget().getOwner() 
                && !carrier.getShipTarget().isDrone()
                && !carrier.getShipTarget().isFighter()) {
            return carrier.getShipTarget();
        }
        
        return ship;     
    }
    
    void setTarget(ShipAPI t) {
        if(target == t) return;
        target = t;
        this.ship.setShipTarget(t);
    }
    
    void goToDestination() {
    	
    	if (target == null || carrier == null) {
    		return;
    	}
    	
        Vector2f to = StolenUtils.toAbsolute(target, targetOffset);
        float distance = MathUtils.getDistance(ship, to);
        
    	float distToCarrier = (float)(MathUtils.getDistanceSquared(carrier.getLocation(),ship.getLocation()) / Math.pow(target.getCollisionRadius(),2));
    	if(target == carrier && distToCarrier < 1.0f || ship.isLanding() == true) {
            float f= 1.0f-Math.min(1,distToCarrier);
            if(returning == false) f = f*0.1f;
            turnToward(target.getFacing());
            ship.getLocation().x = (to.x * (f*0.1f) + ship.getLocation().x * (2 - f*0.1f)) / 2;
            ship.getLocation().y = (to.y * (f*0.1f) + ship.getLocation().y * (2 - f*0.1f)) / 2;
            ship.getVelocity().x = (target.getVelocity().x * f + ship.getVelocity().x * (2 - f)) / 2;
            ship.getVelocity().y = (target.getVelocity().y * f + ship.getVelocity().y * (2 - f)) / 2;
        }else {
        	targetFacingOffset = Float.MIN_VALUE;
            float angleDif = MathUtils.getShortestRotation(ship.getFacing(), VectorUtils.getAngle(ship.getLocation(), to));

            if(Math.abs(angleDif) < 30){
                accelerate();
            } else {
                turnToward(to);
                //decelerate();
            }        
            strafeToward(to);
        }   
    }

    @Override
    public ShipwideAIFlags getAIFlags() {
        return flags;
    }

    @Override
    public void setDoNotFireDelay(float amount) {
    }

    @Override
    public ShipAIConfig getConfig() {
        return config;
    }
    
    public void init() {
    	engine = Global.getCombatEngine();
		fleetManager = engine.getFleetManager(FleetSide.PLAYER);
    	//carrier = ship.getWing().getSourceShip();
    	if (ship.isFighter()) {
    		carrier = ship.getWing().getSourceShip();
    		target = carrier;
        	targetOffset = StolenUtils.toRelative(carrier, carrier.getLocation());
        	range = ship.getWing().getRange();
    	}
    	
    	else {
            if (!ship.isAlive()) return;
    		MutableShipStatsAPI stats = ship.getMutableStats();
    		ShipAPI playerShip = Global.getCombatEngine().getPlayerShip();
    		
    		
    		
    	    for (ShipAPI carrierAI : CombatUtils.getShipsWithinRange(ship.getLocation(), 8000.0F)) { //Checks for carrier within range.
    	        if (carrierAI.getOwner() != ship.getOwner() || 
    	        		carrierAI.getVariant() == null || carrierAI.isPhased() || !carrierAI.hasLaunchBays()) {
    	          continue;
    	        }
    	        if (carrierAI.getOwner() == ship.getOwner()) {
    	        	if (ship == playerShip) //If it is the player, display the text.
    	        		Global.getCombatEngine().maintainStatusForPlayerShip("AceSystem0", "graphics/ui/icons/icon_retreat2.png","CARRIER", carrierAI.getName() ,false);
    	        }
    	        
    	        if (carrierAI != null) {
    	        carrier = carrierAI;
    	        target = carrierAI;
    	        targetOffset = StolenUtils.toRelative(carrierAI, carrierAI.getLocation());    	        
    	        break;
    	        }
    	      }
    	    
    	    //Works
    	    for (ShipAPI fighterAI : CombatUtils.getShipsWithinRange(ship.getLocation(), 4000.0F)) { //Check for fighters nearby to use their landing bay.
      	    	 if (fighterAI.getOwner() != ship.getOwner() || !fighterAI.isFighter()) {
       	          continue;
       	        }

       	        if (ship == playerShip) //If it is the player, display the text.
       	        Global.getCombatEngine().maintainStatusForPlayerShip("AceSystem4", "graphics/ui/icons/icon_retreat2.png","RALLY POINT", fighterAI.getWing().getSourceShip().getName() ,false);
       	        	    	   
    	    	 if (fighterAI != null) {
    	    	fighterbay = fighterAI;
    	    	carrier = fighterAI.getWing().getSourceShip();
    	    	target = carrier;
    	        targetOffset = StolenUtils.toRelative(carrier, fighterAI.getWing().getSource().getLandingLocation(fighterAI));
    	        break;
    	    	} 
    	    }
    	    
    		float CurrentHull = ship.getHitpoints();
    		float MaxHull = ship.getMaxHitpoints();
    		float CurrentCR = ship.getCurrentCR();
    		float CRdeployed = ship.getCRAtDeployment();
    		
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
    		   			
    		//status checks
    			if ((CurrentHull > MaxHull * HPPercent) && (CurrentCR >= CRmarker)){
    				
    				if(hasMissileLeft) {ship.resetDefaultAI();} //Has ammo left
    				
    				if(!hasMissileSlots) {ship.resetDefaultAI();} //Don't even uses missiles, reset
    				
    			}

    			if ((CurrentHull < MaxHull * HPPercent) || (CurrentCR < CRmarker)){
    				
    				if (ship == playerShip) 
    					Global.getCombatEngine().maintainStatusForPlayerShip("AceSystem", "graphics/ui/icons/icon_repair_refit.png","SYSTEMS CRITICAL", "WARNING" ,false);
    			}
    	}
    	
    	
    }
    
}
