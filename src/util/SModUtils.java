package util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.DeployedFleetMemberAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.loading.HullModSpecAPI;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;

public class SModUtils {

    public enum GrowthType {LINEAR, EXPONENTIAL};

    /** Lookup key into the sector-persistent data that stores ship data */
    public static final String SHIP_DATA_KEY = "progsmod_ShipData";
    public static ShipDataTable SHIP_DATA_TABLE = new ShipDataTable();

    public static class Constants {
        /** How many story points it costs to unlock the first extra SMod slot. */
        public static int BASE_EXTRA_SMOD_COST_FRIGATE;
        public static int BASE_EXTRA_SMOD_COST_DESTROYER;
        public static int BASE_EXTRA_SMOD_COST_CRUISER;
        public static int BASE_EXTRA_SMOD_COST_CAPITAL;
        /** Whether the story point increase for unlocking extra SMod slots is linear or exponential. */
        public static GrowthType EXTRA_SMOD_COST_GROWTHTYPE;
        /** If exponential, SP cost is BASE * GROWTH_FACTOR^n, otherwise SP cost is BASE + n*GROWTH_FACTOR. */
        public static float EXTRA_SMOD_COST_GROWTHFACTOR;
        /** The base amount of XP it costs per OP to build in a hull mod is defined as 
         *  p(x) where x is the OP cost of the mod and p is a polynomial with coefficients
         * in [XP_COST_COEFFS] listed in ascending order. */
        public static List<Float> XP_COST_COEFFS;
        /** Additional multipliers that increase XP costs for larger hulls. */
        public static float XP_COST_FRIGATE_MULTIPLIER;
        public static float XP_COST_DESTROYER_MULTIPLIER;
        public static float XP_COST_CRUISER_MULTIPLIER;
        public static float XP_COST_CAPITAL_MULTIPLIER;
        /** How much XP a ship gets refunded when you remove a built-in mod. 
          * Set to something less than 0 to disable removing built-in mods completely. */
        public static float XP_REFUND_FACTOR;
        /** Whether or not ships that get disabled in battle should still get XP */
        public static boolean GIVE_XP_TO_DISABLED_SHIPS;
        /** Whether or not enemy ships that aren't disabled should still award XP */
        public static boolean ONLY_GIVE_XP_FOR_KILLS;
        /** XP gain multiplier */
        public static float XP_GAIN_MULTIPLIER;

        /** Load constants from a json file */
        private static void load(String filePath) throws IOException, JSONException {
            JSONObject json = Global.getSettings().loadJSON(filePath);
            BASE_EXTRA_SMOD_COST_FRIGATE = json.optInt("BaseExtraSModCostFrigate");
            BASE_EXTRA_SMOD_COST_DESTROYER = json.optInt("BaseExtraSModCostDestroyer");
            BASE_EXTRA_SMOD_COST_CRUISER = json.optInt("BaseExtraSModCostCruiser");
            BASE_EXTRA_SMOD_COST_CAPITAL = json.optInt("BaseExtraSModCostCapital");
            EXTRA_SMOD_COST_GROWTHTYPE = 
                json.optInt("extraSModCostGrowthType") == 0 ? GrowthType.LINEAR : GrowthType.EXPONENTIAL;
            EXTRA_SMOD_COST_GROWTHFACTOR = (float) json.optDouble("extraSModCostGrowthFactor");
            JSONArray coeffArray = json.getJSONArray("xpCostCoefficients");
            XP_COST_COEFFS = new ArrayList<>();
            for (int i = 0; i < coeffArray.length(); i++) {
                XP_COST_COEFFS.add((float) coeffArray.optDouble(i));
            }
            XP_COST_FRIGATE_MULTIPLIER = (float) json.optDouble("xpCostFrigateMultiplier");
            XP_COST_DESTROYER_MULTIPLIER = (float) json.optDouble("xpCostDestroyerMultiplier");
            XP_COST_CRUISER_MULTIPLIER = (float) json.optDouble("xpCostCruiserMultiplier");
            XP_COST_CAPITAL_MULTIPLIER = (float) json.optDouble("xpCostCapitalMultiplier");
            XP_REFUND_FACTOR = (float) json.optDouble("xpRefundFactor");
            GIVE_XP_TO_DISABLED_SHIPS = json.optBoolean("giveXPToDisabledShips");
            ONLY_GIVE_XP_FOR_KILLS = json.optBoolean("onlyGiveXPForKills");
            XP_GAIN_MULTIPLIER = (float) json.optDouble("xpGainMultiplier");
        }
    }

    /** Contains XP and # of max perma mods over the normal limit. */
    public static class ShipData {
        public float xp = 0;
        public int permaModsOverLimit = 0;

        public ShipData(float xp, int pmol) {
            this.xp = xp;
            permaModsOverLimit = pmol;
        }
    }

    /** Wrapper class that maps ships to their ship data. */
    public static class ShipDataTable extends HashMap<String, ShipData> {}

    // /** Wrapper class for ShipAPI for use as hash keys. */
    // public static class HashableShipAPI {
    //     public ShipAPI ship;

    //     public HashableShipAPI(ShipAPI ship) {
    //         this.ship = ship;
    //     }

    //     @Override
    //     public boolean equals(Object o) {
    //         if (!(o instanceof HashableShipAPI)) return false;
    //         return ship.getId().equals(((HashableShipAPI) o).ship.getId());
    //     }

    //     @Override
    //     public int hashCode() {
    //         return ship.getId().hashCode();
    //     }
    // }

        
    public static void loadConstants(String filePath) {
        try {
            Constants.load(filePath);
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }
    }

    /** Retrieve the persistent data for this mod, if it exists. Else create it. */
    public static void loadShipData() {
        if (!Global.getSector().getPersistentData().containsKey(SModUtils.SHIP_DATA_KEY)) {
            SHIP_DATA_TABLE = new ShipDataTable();
            Global.getSector().getPersistentData().put(SModUtils.SHIP_DATA_KEY, SModUtils.SHIP_DATA_TABLE); 
        }
        else {
            SHIP_DATA_TABLE = (ShipDataTable) Global.getSector().getPersistentData().get(SModUtils.SHIP_DATA_KEY);
        }
    }

    /** Gets the story point cost of increasing the number of built-in hullmods of [ship] by 1. */
    public static int getStoryPointCost(FleetMemberAPI ship) {
        int baseCost;
        switch (ship.getVariant().getHullSize()) {
            case FRIGATE: baseCost = Constants.BASE_EXTRA_SMOD_COST_FRIGATE; break;
            case DESTROYER: baseCost = Constants.BASE_EXTRA_SMOD_COST_DESTROYER; break;
            case CRUISER: baseCost = Constants.BASE_EXTRA_SMOD_COST_CRUISER; break;
            case CAPITAL_SHIP: baseCost = Constants.BASE_EXTRA_SMOD_COST_CAPITAL; break;
            default: baseCost = 0;
        }
        
        String shipId = ship.getId();

        if (!SHIP_DATA_TABLE.containsKey(shipId)) {
            return baseCost;
        }

        int modsOverLimit = SHIP_DATA_TABLE.get(shipId).permaModsOverLimit;

        return Constants.EXTRA_SMOD_COST_GROWTHTYPE == GrowthType.EXPONENTIAL ? 
            (int) (baseCost * Math.pow(Constants.EXTRA_SMOD_COST_GROWTHFACTOR, modsOverLimit)) : 
            (int) (baseCost + modsOverLimit * Constants.EXTRA_SMOD_COST_GROWTHFACTOR);
    }

    /** Gets the XP cost of building in a certain hullmod */
    public static int getBuildInCost(HullModSpecAPI hullMod, HullSize size) {
        switch (size) {
            case FRIGATE: 
                return (int) (
                    computePolynomial(hullMod.getFrigateCost(), Constants.XP_COST_COEFFS) * Constants.XP_COST_FRIGATE_MULTIPLIER
                );
            case DESTROYER:
                return (int) (
                    computePolynomial(hullMod.getDestroyerCost(), Constants.XP_COST_COEFFS) * Constants.XP_COST_DESTROYER_MULTIPLIER
                );
            case CRUISER:
                return (int) (
                    computePolynomial(hullMod.getCruiserCost(), Constants.XP_COST_COEFFS) * Constants.XP_COST_CRUISER_MULTIPLIER
                );
            case CAPITAL_SHIP:
                return (int) (
                    computePolynomial(hullMod.getCapitalCost(), Constants.XP_COST_COEFFS) * Constants.XP_COST_CAPITAL_MULTIPLIER
                );
            default: return 0;
        }
    }

    // private enum FleetMemberType {DFM, FM, SHIP};

    // public static <T> List<String> getFleetMemberIds(List<? extends T> fleetMembers) {
    //     int n = fleetMembers.size();
    //     List<String> ids = new ArrayList<>(n);
    //     FleetMemberType type = null;
    //     for (T fm : fleetMembers) {
    //         if (type == null) {
    //             if (fm instanceof FleetMemberAPI) {type = FleetMemberType.FM;}
    //             else if (fm instanceof DeployedFleetMemberAPI) {type = FleetMemberType.DFM;}
    //             else if (fm instanceof ShipAPI) {type = FleetMemberType.SHIP;}
    //             else return ids;
    //         }
    //         switch (type) {
    //             case FM: ids.add(((FleetMemberAPI) fm).getId()); break;
    //             case DFM: ids.add(((DeployedFleetMemberAPI) fm).getMember().getId()); break;
    //             case SHIP: ids.add(((ShipAPI) fm).getFleetMemberId()); break;
    //             default: break;
    //         }
    //     }
    //     return ids;
    // }

    /** Given a list of fleetMembers, return a list of their ids */
    public static List<String> getFleetMemberIds(List<FleetMemberAPI> fleetMembers) {
        List<String> ids = new ArrayList<>(fleetMembers.size());
        for (FleetMemberAPI fm : fleetMembers) {
            ids.add(fm.getId());
        }
        return ids; 
    }

    /** Given a list of deployedFleetMembers, return a list of their ids */
    public static List<String> getDeployedFleetMemberIds(List<DeployedFleetMemberAPI> deployedFleetMembers) {
        List<String> ids = new ArrayList<>(deployedFleetMembers.size());
        for (DeployedFleetMemberAPI fm : deployedFleetMembers) {
            ids.add(fm.getMember().getId());
        }
        return ids; 
    }

    /** Given a fleet member, return its S-Mod limit */
    public static int getMaxSMods(FleetMemberAPI fleetMember) {
        ShipData shipData = SHIP_DATA_TABLE.get(fleetMember.getId());
        int overLimit = shipData == null ? 0 : shipData.permaModsOverLimit;
        return overLimit + (int) fleetMember.getStats()
                                .getDynamic()
                                .getMod(Stats.MAX_PERMANENT_HULLMODS_MOD)
                                .computeEffective(Global.getSettings().getInt("maxPermanentHullmods"));
    }

    /** Writes data about [fleetMember]'s built-in hull mods into MemKeys.LOCAL */
    public static void writeShipDataToMemory(FleetMemberAPI fleetMember, Map<String, MemoryAPI> memoryMap) {
        int maxSMods = getMaxSMods(fleetMember);
        int currentSMods = fleetMember.getVariant().getSMods().size();
        memoryMap.get(MemKeys.LOCAL).set("$selectedShipMax", maxSMods, 0f);
        memoryMap.get(MemKeys.LOCAL).set("$selectedShipMaxPlusOne", maxSMods + 1, 0f);
        memoryMap.get(MemKeys.LOCAL).set("$selectedShipCurrent", currentSMods, 0f);
        memoryMap.get(MemKeys.LOCAL).set("$selectedShipRemaining", maxSMods - currentSMods, 0f);
        memoryMap.get(MemKeys.LOCAL).set("$selectedShipName", fleetMember.getShipName());
        memoryMap.get(MemKeys.LOCAL).set("$xpRefund", (int) (100 * SModUtils.Constants.XP_REFUND_FACTOR), 0f);
        memoryMap.get(MemKeys.LOCAL).set("$nextStoryPointCost", SModUtils.getStoryPointCost(fleetMember), 0f);
        ShipData shipData = SHIP_DATA_TABLE.get(fleetMember.getId());
        int overLimit = shipData == null ? 0 : shipData.permaModsOverLimit;
        memoryMap.get(MemKeys.LOCAL).set("$augmentTimes", overLimit);
        memoryMap.get(MemKeys.LOCAL).set("$modSingOrPlural", maxSMods - currentSMods == 1 ? "mod" : "mods");
        memoryMap.get(MemKeys.LOCAL).set("$timeSingOrPlural", overLimit == 1 ? "time" : "times");
    }

    /** Polynomial coefficients are listed in [coeff] lowest order first. */
    public static float computePolynomial(int x, List<Float> coeff) {
        int s = coeff.size() - 1;
        float result = coeff.get(s);
        for (int i = s; i >= 0; i--) {
            result += result*s + coeff.get(i);
        }
        return result;
    }
}