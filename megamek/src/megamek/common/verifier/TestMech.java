/*
 * MegaMek -
 * Copyright (C) 2000-2005 Ben Mazur (bmazur@sev.org)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * for more details.
 */
package megamek.common.verifier;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.stream.Collectors;

import megamek.common.*;
import megamek.common.annotations.Nullable;
import megamek.common.equipment.ArmorType;
import megamek.common.equipment.WeaponMounted;
import megamek.common.options.OptionsConstants;
import megamek.common.util.StringUtil;
import megamek.common.weapons.artillery.ArtilleryWeapon;
import megamek.common.weapons.autocannons.ACWeapon;
import megamek.common.weapons.autocannons.LBXACWeapon;
import megamek.common.weapons.autocannons.UACWeapon;
import megamek.common.weapons.gaussrifles.GaussWeapon;
import megamek.common.weapons.lasers.EnergyWeapon;
import megamek.common.weapons.ppc.PPCWeapon;

/**
 * @author Reinhard Vicinus
 */
public class TestMech extends TestEntity {

    public enum MechJumpJets {
        JJ_STANDARD (EquipmentTypeLookup.JUMP_JET, true, Mek.JUMP_STANDARD),
        JJ_IMPROVED (EquipmentTypeLookup.IMPROVED_JUMP_JET, false, Mek.JUMP_IMPROVED),
        JJ_PROTOTYPE (EquipmentTypeLookup.PROTOTYPE_JUMP_JET, true, Mek.JUMP_PROTOTYPE),
        JJ_PROTOTYPE_IMPROVED (EquipmentTypeLookup.PROTOTYPE_IMPROVED_JJ, false, Mek.JUMP_PROTOTYPE_IMPROVED),
        JJ_UMU (EquipmentTypeLookup.MECH_UMU, false, Mek.JUMP_NONE),
        JJ_BOOSTER (EquipmentTypeLookup.MECH_JUMP_BOOSTER, true, Mek.JUMP_BOOSTER);

        private String internalName;
        private boolean industrial;
        private int jumpType;

        MechJumpJets(String internalName, boolean industrial, int jumpType) {
            this.internalName = internalName;
            this.industrial = industrial;
            this.jumpType = jumpType;
        }

        public String getName() {
            return internalName;
        }

        public boolean canIndustrialUse() {
            return industrial;
        }

        public int getJumpType() {
            return jumpType;
        }

        public static List<EquipmentType> allJJs(boolean industrialOnly) {
            List<EquipmentType> retVal = new ArrayList<>();
            for (MechJumpJets jj : values()) {
                if (jj.industrial || !industrialOnly) {
                    retVal.add(EquipmentType.get(jj.internalName));
                }
            }
            return retVal;
        }

    }

    /**
     * Filters all mech armor according to given tech constraints
     *
     * @param etype        Entity type bitmap
     * @param industrial   Whether to include industrialmech armors
     * @param techManager  The tech manager that determines legality
     * @return             A list of legal armors for the mech
     */
    public static List<ArmorType> legalArmorsFor(long etype, boolean industrial, ITechManager techManager) {
        List<ArmorType> legalArmors = new ArrayList<>();
        boolean industrialOnly = industrial
                && (techManager.getTechLevel().ordinal() < SimpleTechLevel.EXPERIMENTAL.ordinal());
        boolean isLam = (etype & Entity.ETYPE_LAND_AIR_MEK) != 0;
        for (ArmorType armor : ArmorType.allArmorTypes()) {
            if ((armor.getArmorType() == EquipmentType.T_ARMOR_PATCHWORK)
                    || (isLam && (armor.getArmorType() == EquipmentType.T_ARMOR_HARDENED))) {
                continue;
            }
            if (armor.hasFlag(MiscType.F_MECH_EQUIPMENT)
                    && ((armor.getArmorType() != EquipmentType.T_ARMOR_COMMERCIAL) || industrial)
                    && techManager.isLegal(armor)
                    && (!isLam || (armor.getCriticals(null) == 0))
                    && (!industrialOnly || armor.isIndustrial())) {
                legalArmors.add(armor);
            }
        }
        return legalArmors;
    }

    private Mek mech;

    public TestMech(Mek mech, TestEntityOption option, String fileString) {
        super(option, mech.getEngine(), getStructure(mech));
        this.mech = mech;
        this.fileString = fileString;
    }

    private static Structure getStructure(Mek mech) {
        int type = mech.getStructureType();
        return new Structure(type, mech.isSuperHeavy(), mech.getMovementMode());
    }

    public static Integer maxJumpMP(Mek mech) {
        if (mech.isSuperHeavy()) {
            return 0;
        }
        if (mech.getJumpType() == Mek.JUMP_BOOSTER) {
            return null;
        } else if (!mech.hasEngine() || (!mech.getEngine().isFusion() && (mech.getEngine().getEngineType() != Engine.FISSION))) {
            return 0;
        } else if ((mech.getJumpType() == Mek.JUMP_IMPROVED)
                || (mech.getJumpType() == Mek.JUMP_PROTOTYPE_IMPROVED)) {
            return (int) Math.ceil(mech.getOriginalWalkMP() * 1.5);
        } else {
            return mech.getOriginalWalkMP();
        }
    }

    @Override
    public Entity getEntity() {
        return mech;
    }

    @Override
    public boolean isTank() {
        return false;
    }

    @Override
    public boolean isMech() {
        return true;
    }

    @Override
    public boolean isAero() {
        return false;
    }

    @Override
    public boolean isSmallCraft() {
        return false;
    }

    @Override
    public boolean isAdvancedAerospace() {
        return false;
    }

    @Override
    public boolean isProtomech() {
        return false;
    }

    @Override
    public double getWeightMisc() {
        // LAM/QuadVee equipment is 10% of mass, rounded up to whole number (15% for bimodal LAM).
        // IO p. 113 (LAM), 134 (QV)
        if (mech instanceof LandAirMech) {
            return Math.ceil(mech.getWeight() *
                    (((LandAirMech) mech).getLAMType() == LandAirMech.LAM_BIMODAL ? 0.15 : 0.1));
        } else if (mech instanceof QuadVee) {
            return Math.ceil(mech.getWeight() * 0.1);
        }
        return 0.0;
    }

    @Override
    public double getWeightPowerAmp() {
        if (!mech.hasEngine()
                    || (mech.getEngine().getEngineType() == Engine.COMBUSTION_ENGINE)
                    || (mech.getEngine().getEngineType() == Engine.FUEL_CELL)) {
            double powerAmpWeight = 0;
            for (Mounted m : mech.getWeaponList()) {
                WeaponType wt = (WeaponType) m.getType();
                if (wt instanceof EnergyWeapon) {
                    powerAmpWeight += m.getTonnage();
                }
                if ((m.getLinkedBy() != null)
                        && (m.getLinkedBy().getType() instanceof MiscType)
                        && m.getLinkedBy().getType().hasFlag(MiscType.F_PPC_CAPACITOR)) {
                    powerAmpWeight += m.getLinkedBy().getTonnage();
                }
            }
            return TestEntity.ceil(powerAmpWeight / 10f, getWeightCeilingPowerAmp());
        }
        return 0;
    }

    public double getWeightCockpit() {
        switch (mech.getCockpitType()) {
            case Mek.COCKPIT_SMALL:
                return 2.0;
            case Mek.COCKPIT_TORSO_MOUNTED:
            case Mek.COCKPIT_DUAL:
            case Mek.COCKPIT_SUPERHEAVY:
            case Mek.COCKPIT_SUPERHEAVY_INDUSTRIAL:
            case Mek.COCKPIT_TRIPOD:
            case Mek.COCKPIT_TRIPOD_INDUSTRIAL:
            case Mek.COCKPIT_INTERFACE:
            case Mek.COCKPIT_QUADVEE:
                return 4.0;
            case Mek.COCKPIT_PRIMITIVE:
            case Mek.COCKPIT_PRIMITIVE_INDUSTRIAL:
            case Mek.COCKPIT_SUPERHEAVY_TRIPOD:
            case Mek.COCKPIT_SUPERHEAVY_TRIPOD_INDUSTRIAL:
            case Mek.COCKPIT_SMALL_COMMAND_CONSOLE:
                return 5.0;
            case Mek.COCKPIT_COMMAND_CONSOLE:
                return 6.0;
            case Mek.COCKPIT_SUPERHEAVY_COMMAND_CONSOLE:
                return 7.0;
            case Mek.COCKPIT_STANDARD:
            case Mek.COCKPIT_INDUSTRIAL:
            case Mek.COCKPIT_VRRP:
            default:
                return 3.0;
        }
    }

    public double getWeightGyro() {
        double retVal = Math.ceil(engine.getRating() / 100.0f);
        if (mech.getGyroType() == Mek.GYRO_XL) {
            retVal /= 2;
        } else if (mech.getGyroType() == Mek.GYRO_COMPACT) {
            retVal *= 1.5;
        } else if ((mech.getGyroType() == Mek.GYRO_HEAVY_DUTY)
                || (mech.getGyroType() == Mek.GYRO_SUPERHEAVY)) {
            retVal *= 2;
        } else if (mech.getGyroType() == Mek.GYRO_NONE) {
            retVal = 0;
        }
        retVal = ceil(retVal, getWeightCeilingGyro());
        return retVal;
    }

    @Override
    public double getWeightControls() {
        return getWeightCockpit() + getWeightGyro();
    }

    @Override
    public int getCountHeatSinks() {
        return mech.heatSinks();
    }

    @Override
    public double getWeightHeatSinks() {
        boolean hasCompact = false;
        double compactHsTons = 0;
        for (Mounted misc : mech.getMisc()) {
            if (misc.getType().hasFlag(MiscType.F_COMPACT_HEAT_SINK)) {
                hasCompact = true;
                compactHsTons += misc.getTonnage();
            }
        }
        if (hasCompact) {
            return compactHsTons
                    - (engine.getWeightFreeEngineHeatSinks() * 1.5f);
        } else {
            return mech.heatSinks() - engine.getWeightFreeEngineHeatSinks();
        }
    }

    @Override
    public boolean hasDoubleHeatSinks() {
        return mech.hasDoubleHeatSinks();
    }

    @Override
    public String printWeightMisc() {
        return "";
    }

    @Override
    public String printWeightControls() {
        StringBuffer retVal = new StringBuffer(StringUtil.makeLength(
                mech.getCockpitTypeString() + ":", getPrintSize() - 5));
        retVal.append(makeWeightString(getWeightCockpit()));
        retVal.append("\n");
        retVal.append(StringUtil.makeLength(mech.getGyroTypeString() + ":",
                getPrintSize() - 5));
        retVal.append(makeWeightString(getWeightGyro()));
        retVal.append("\n");
        return retVal.toString();
    }

    public Mek getMech() {
        return mech;
    }

    public boolean isCockpitLocation(int location) {
        if (mech.getCockpitType() == Mek.COCKPIT_TORSO_MOUNTED
                || mech.getCockpitType() == Mek.COCKPIT_VRRP) {
            return location == Mek.LOC_CT;
        }
        return location == Mek.LOC_HEAD;
    }

    public boolean isEngineLocation(int location) {
        return mech.hasSystem(Mek.SYSTEM_ENGINE, location);
    }

    public int countCriticalSlotsFromEquipInLocation(Entity entity, Mounted mount,
            int location) {
        int count = 0;
        for (int slots = 0; slots < entity.getNumberOfCriticals(location); slots++) {
            CriticalSlot slot = entity.getCritical(location, slots);
            if ((slot == null) || (slot.getType() == CriticalSlot.TYPE_SYSTEM)) {
                continue;
            } else if (slot.getType() == CriticalSlot.TYPE_EQUIPMENT) {
                if (slot.getMount().equals(mount)) {
                    count++;
                }
            } else {
                // Ignore this?
            }
        }
        return count;
    }

    public boolean checkMiscSpreadAllocation(Entity entity, Mounted mounted,
            StringBuffer buff) {
        MiscType mt = (MiscType) mounted.getType();
        if (mt.hasFlag(MiscType.F_STEALTH) && !entity.hasPatchworkArmor()) {
            // stealth needs to have 2 crits in legs arm and side torso
            if (countCriticalSlotsFromEquipInLocation(entity, mounted,
                    Mek.LOC_LARM) != 2) {
                buff.append("incorrect number of stealth crits in left arm\n");
                return false;
            }
            if (countCriticalSlotsFromEquipInLocation(entity, mounted,
                    Mek.LOC_RARM) != 2) {
                buff.append("incorrect number of stealth crits in right arm\n");
                return false;
            }
            if (countCriticalSlotsFromEquipInLocation(entity, mounted,
                    Mek.LOC_LLEG) != 2) {
                buff.append("incorrect number of stealth crits in left leg\n");
                return false;
            }
            if (countCriticalSlotsFromEquipInLocation(entity, mounted,
                    Mek.LOC_RLEG) != 2) {
                buff.append("incorrect number of stealth crits in right leg\n");
                return false;
            }
            if (countCriticalSlotsFromEquipInLocation(entity, mounted, Mek.LOC_LT) != 2) {
                buff.append("incorrect number of stealth crits in left torso\n");
                return false;
            }
            if (countCriticalSlotsFromEquipInLocation(entity, mounted, Mek.LOC_RT) != 2) {
                buff.append("incorrect number of stealth crits in right torso\n");
                return false;
            }
        }
        if (mt.hasFlag(MiscType.F_DRONE_CONTROL_CONSOLE)) {
            if (mounted.getLocation() != Mek.LOC_HEAD) {
                buff.append("Drone Control Console must be mounted in head");
                return false;
            }
        }
        if (mt.hasFlag(MiscType.F_MOBILE_HPG)) {
            if ((countCriticalSlotsFromEquipInLocation(entity, mounted,
                    Mek.LOC_LARM) > 0)
                    || (countCriticalSlotsFromEquipInLocation(entity, mounted,
                            Mek.LOC_RARM) > 0)
                    || (countCriticalSlotsFromEquipInLocation(entity, mounted,
                            Mek.LOC_HEAD) > 0)
                    || (countCriticalSlotsFromEquipInLocation(entity, mounted,
                            Mek.LOC_LLEG) > 0)
                    || (countCriticalSlotsFromEquipInLocation(entity, mounted,
                            Mek.LOC_RLEG) > 0)) {
                buff.append("ground mobile HPG must be mounted in torso locations\n");
            }
        }
        if (mt.hasFlag(MiscType.F_ENVIRONMENTAL_SEALING)) {
            // environmental sealing needs to have 1 crit per location
            for (int locations = 0; locations < entity.locations(); locations++) {
                if (countCriticalSlotsFromEquipInLocation(entity, mounted,
                        locations) != 1) {
                    buff.append("not an environmental sealing crit in each location\n");
                    return false;
                }
            }
        }
        if (mt.hasFlag(MiscType.F_BLUE_SHIELD)) {
            // blue shield needs to have 1 crit per location, except head
            for (int locations = 0; locations < entity.locations(); locations++) {
                if (locations != Mek.LOC_HEAD) {
                    if (countCriticalSlotsFromEquipInLocation(entity, mounted,
                            locations) != 1) {
                        buff.append("not a blue shield crit in each location except the head\n");
                        return false;
                    }
                }

            }
        }
        if (mt.hasFlag(MiscType.F_PARTIAL_WING)) {
            // partial wing needs 3/4 crits in the side torsos
            if (countCriticalSlotsFromEquipInLocation(entity, mounted, Mek.LOC_LT) != ((TechConstants.isClan(mt
                    .getTechLevel(entity.getTechLevelYear()))) ? 3
                    : 4)) {
                buff.append("incorrect number of partial wing crits in left torso\n");
                return false;
            }
            if (countCriticalSlotsFromEquipInLocation(entity, mounted, Mek.LOC_RT) != ((TechConstants.isClan(mt
                    .getTechLevel(entity.getTechLevelYear()))) ? 3
                    : 4)) {
                buff.append("incorrect number of partial wing crits in right torso\n");
                return false;
            }
        }
        return true;
    }

    public boolean criticalSlotsAllocated(Entity entity, Mounted mounted,
            Vector<Serializable> allocation, StringBuffer buff) {
        int location = mounted.getLocation();
        EquipmentType et = mounted.getType();
        int criticals;
        if (et instanceof MiscType) {
            criticals = calcMiscCrits((MiscType) et, mounted.getSize());
        } else {
            criticals = mounted.getCriticals();
        }
        int count = 0;

        if (location == Entity.LOC_NONE) {
            return true;
        }

        if (et.isSpreadable() && !et.getName().equals("Targeting Computer")) {
            for (int locations = 0; locations < entity.locations(); locations++) {
                count += countCriticalSlotsFromEquipInLocation(entity, mounted,
                        locations);
            }
        } else {
            count = countCriticalSlotsFromEquipInLocation(entity, mounted,
                    location);
        }

        if ((et instanceof WeaponType) && mounted.isSplit()) {
            int secCound;
            for (int locations = 0; locations < entity.locations(); locations++) {
                if (locations == location) {
                    continue;
                }

                secCound = countCriticalSlotsFromEquipInLocation(entity, mounted,
                        locations);
                if ((secCound != 0)
                        && (location == Mek.mostRestrictiveLoc(locations,
                                location))) {
                    count += secCound;
                    break;
                }
            }
        }

        if (count == criticals) {
            return true;
        }

        allocation.addElement(mounted);
        allocation.addElement(criticals);
        allocation.addElement(count);
        return false;
    }

    public boolean checkCriticalSlotsForEquipment(Mek mech,
            Vector<Mounted> unallocated, Vector<Serializable> allocation,
            Vector<Integer> heatSinks, StringBuffer buff) {
        boolean legal = true;
        int countInternalHeatSinks = 0;
        for (Mounted m : mech.getEquipment()) {
            int loc = m.getLocation();
            if (loc == Entity.LOC_NONE) {
                if ((m.getType() instanceof AmmoType)
                        && (m.getUsableShotsLeft() <= 1)) {
                    continue;
                }
                if (m.getCriticals() == 0) {
                    continue;
                }
                if (!(m.getType() instanceof MiscType)) {
                    unallocated.addElement(m);
                    continue;
                }
                MiscType mt = (MiscType) m.getType();
                if (mt.hasFlag(MiscType.F_HEAT_SINK)
                        || mt.hasFlag(MiscType.F_DOUBLE_HEAT_SINK)
                        || mt.hasFlag(MiscType.F_IS_DOUBLE_HEAT_SINK_PROTOTYPE)) {
                    countInternalHeatSinks++;
                } else {
                    unallocated.addElement(m);
                    continue;
                }
            }
            // Check for illegal allocations
            if (mech.isOmni()
                    && (mech instanceof BipedMech)
                    && ((loc == Mek.LOC_LARM) || (loc == Mek.LOC_RARM))
                    && ((m.getType() instanceof GaussWeapon)
                            || (m.getType() instanceof ACWeapon)
                            || (m.getType() instanceof UACWeapon)
                            || (m.getType() instanceof LBXACWeapon)
                            || (m.getType() instanceof PPCWeapon))) {
                String weapon = "";
                if (m.getType() instanceof GaussWeapon) {
                    weapon = "gauss rifles";
                } else if ((m.getType() instanceof ACWeapon)
                        || (m.getType() instanceof UACWeapon)
                        || (m.getType() instanceof LBXACWeapon)) {
                    weapon = "autocannons";
                } else if (m.getType() instanceof PPCWeapon) {
                    weapon = "PPCs";
                }
                if (mech.hasSystem(Mek.ACTUATOR_LOWER_ARM, loc)
                        || mech.hasSystem(Mek.ACTUATOR_HAND, loc)) {
                    buff.append("Omni mechs with arm mounted ").append(weapon)
                            .append(" cannot have lower armor or hand actuators!\n");
                    legal = false;
                }
            }
        }
        if ((countInternalHeatSinks > engine.integralHeatSinkCapacity(this.mech.hasCompactHeatSinks()))
                || ((countInternalHeatSinks < engine.integralHeatSinkCapacity(this.mech.hasCompactHeatSinks()))
                        && (countInternalHeatSinks != mech.heatSinks(false))
                && !mech.isOmni())) {
            heatSinks.addElement(countInternalHeatSinks);
        }
        return legal;
    }

    public boolean correctCriticals(StringBuffer buff) {
        Vector<Mounted> unallocated = new Vector<>();
        Vector<Serializable> allocation = new Vector<>();
        Vector<Integer> heatSinks = new Vector<>();
        boolean correct = checkCriticalSlotsForEquipment(mech, unallocated, allocation, heatSinks, buff);
        if (!unallocated.isEmpty()) {
            buff.append("Unallocated Equipment:\n");
            for (Mounted mount : unallocated) {
                buff.append(mount.getType().getInternalName()).append("\n");
            }
            correct = false;
        }
        if (!allocation.isEmpty()) {
            buff.append("Allocated Equipment:\n");
            for (Enumeration<Serializable> serializableEnum = allocation.elements();
                 serializableEnum.hasMoreElements();) {
                Mounted mount = (Mounted) serializableEnum.nextElement();
                int needCrits = (Integer) serializableEnum.nextElement();
                int aktCrits = (Integer) serializableEnum.nextElement();
                buff.append(mount.getType().getInternalName()).append(" has ")
                        .append(needCrits).append(" Slots, but ")
                        .append(aktCrits).append(" Slots are allocated!")
                        .append("\n");
            }
            correct = false;
        }
        if (!heatSinks.isEmpty()) {
            int sinks = heatSinks.elements().nextElement();
            buff.append(sinks - engine.integralHeatSinkCapacity(mech
                    .hasCompactHeatSinks())).append(" unallocated heat sinks\n");
            correct = false;
        }
        if (!checkSystemCriticals(buff)) {
            correct = false;
        }
        return correct;
    }

    private boolean checkSystemCriticals(StringBuffer buff) {
        // Engine criticals
        boolean engineCorrect = true;
        int requiredSideCrits = engine.getSideTorsoCriticalSlots().length;
        if ((requiredSideCrits != mech.getNumberOfCriticals(
                CriticalSlot.TYPE_SYSTEM, Mek.SYSTEM_ENGINE, Mek.LOC_LT))
                || (requiredSideCrits != mech.getNumberOfCriticals(
                        CriticalSlot.TYPE_SYSTEM, Mek.SYSTEM_ENGINE,
                        Mek.LOC_RT))) {
            engineCorrect = false;
        }
        int requiredCTCrits = engine.getCenterTorsoCriticalSlots(mech.getGyroType()).length;
        if (requiredCTCrits != mech
                .getNumberOfCriticals(CriticalSlot.TYPE_SYSTEM,
                        Mek.SYSTEM_ENGINE, Mek.LOC_CT)) {
            engineCorrect = false;
        }
        if (!engineCorrect) {
            buff.append("Engine: Incorrect number of criticals allocated.\n");
        }

        if (!engineCorrect) {
            return false;
        }

        if (getMech().getGyroType() == Mek.GYRO_NONE
                && getMech().getCockpitType() != Mek.COCKPIT_INTERFACE) {
            buff.append("Missing Gyro!.\n");
            return false;
        }

        return true;
    }

    public String printArmorLocProp(int loc, int wert) {
        return " is greater than " + wert + "!";
    }

    public boolean correctArmor(StringBuffer buff) {
        boolean correct = true;
        for (int loc = 0; loc < mech.locations(); loc++) {
            if (loc == Mek.LOC_HEAD) {
                if (((mech.getOArmor(Mek.LOC_HEAD) > 9) && !mech.isSuperHeavy()) || ((mech.getOArmor(Mek.LOC_HEAD) > 12) && mech.isSuperHeavy())) {
                    buff.append(printArmorLocation(Mek.LOC_HEAD))
                            .append(printArmorLocProp(Mek.LOC_HEAD, 9))
                            .append("\n");
                    correct = false;
                }

            } else if ((mech.getOArmor(loc) + (mech.hasRearArmor(loc) ? mech
                    .getOArmor(loc, true) : 0)) > (2 * mech.getOInternal(loc))) {
                buff.append(printArmorLocation(loc))
                        .append(printArmorLocProp(loc,
                                2 * mech.getOInternal(loc))).append("\n");
                correct = false;
            }
        }

        if (!getEntity().hasPatchworkArmor()
                && (getEntity().getLabTotalArmorPoints() < getEntity().getTotalOArmor())) {
            correct = false;
            buff.append("Too many armor points allocated");
        }

        return correct;
    }

    public boolean correctMovement(StringBuffer buff) {
        // Mechanical Jump Boosts can be greater then Running as long as
        // the unit can handle the weight.
        if ((mech.getJumpMP(MPCalculationSetting.NO_GRAVITY) > mech.getOriginalRunMP())
                && !mech.hasJumpBoosters()
                && !mech.hasWorkingMisc(MiscType.F_PARTIAL_WING)) {
            buff.append("Jump MP exceeds run MP\n");
            return false;
        }
        if ((mech.getJumpMP(MPCalculationSetting.NO_GRAVITY) > mech.getOriginalWalkMP())
                && (((mech.getJumpType() != Mek.JUMP_IMPROVED) && (mech.getJumpType() != Mek.JUMP_PROTOTYPE_IMPROVED))
                        && !mech.hasWorkingMisc(MiscType.F_PARTIAL_WING) && !mech
                            .hasJumpBoosters())) {
            buff.append("Jump MP exceeds walk MP without IJJs\n");
            return false;
        }
        if ((mech instanceof LandAirMech) && mech.getJumpMP(MPCalculationSetting.NO_GRAVITY) < 3) {
            buff.append("LAMs must have at least 3 jumping MP.\n");
            return false;
        }
        return true;
    }

    @Override
    public boolean correctEntity(StringBuffer buff, int ammoTechLvl) {
        boolean correct = true;
        if (skip()) {
            return true;
        }
        if (!correctWeight(buff)) {
            buff.insert(0, printTechLevel() + printShortMovement());
            buff.append(printWeightCalculation());
            correct = false;
        }
        if (!engine.engineValid) {
            buff.append(engine.problem.toString()).append("\n\n");
            correct = false;
        }
        if (getCountHeatSinks() < engine.getWeightFreeEngineHeatSinks()) {
            buff.append("Heat Sinks:\n");
            buff.append(" Engine    "
                    + engine.integralHeatSinkCapacity(mech
                            .hasCompactHeatSinks()) + "\n");
            buff.append(" Total     " + getCountHeatSinks() + "\n");
            buff.append(" Required  " + engine.getWeightFreeEngineHeatSinks()
                    + "\n");
            correct = false;
        }
        if (showCorrectArmor() && !correctArmor(buff)) {
            correct = false;
        }
        if (showCorrectCritical() && !correctCriticals(buff)) {
            correct = false;
        }
        if (showFailedEquip() && hasFailedEquipment(buff)) {
            correct = false;
        }
        if (hasIllegalTechLevels(buff, ammoTechLvl)) {
            correct = false;
        }
        if (showIncorrectIntroYear() && hasIncorrectIntroYear(buff)) {
            correct = false;
        }
        if (hasIllegalEquipmentCombinations(buff)) {
            correct = false;
        }
        for (Mounted misc : mech.getMisc()) {
            correct = correct && checkMiscSpreadAllocation(mech, misc, buff);
        }
        correct = correct && correctMovement(buff);
        if (getEntity().hasQuirk(OptionsConstants.QUIRK_NEG_ILLEGAL_DESIGN)) {
            correct = true;
        }
        return correct;
    }

    @Override
    public StringBuffer printEntity() {
        StringBuffer buff = new StringBuffer();
        buff.append("Mech: ").append(mech.getDisplayName()).append("\n");
        buff.append("Found in: ").append(fileString).append("\n");
        buff.append(printTechLevel());
        buff.append("Intro year: ").append(mech.getYear()).append("\n");
        buff.append(printSource());
        buff.append(printShortMovement());
        if (correctWeight(buff, true, true)) {
            buff.append("Weight: ").append(getWeight()).append(" (")
                    .append(calculateWeight()).append(")\n");
        }
        buff.append(printWeightCalculation()).append("\n");
        buff.append(printArmorPlacement());
        correctArmor(buff);
        buff.append(printLocations());
        correctCriticals(buff);

        // printArmor(buff);
        printFailedEquipment(buff);
        return buff;
    }

    @Override
    public String getName() {
        return "Mech: " + mech.getDisplayName();
    }

    /**
     * calculates the total weight of all armored components.
     */
    @Override
    public double getArmoredComponentWeight() {
        double weight = 0.0;
        double cockpitWeight = 0.0;
        for (int location = Mek.LOC_HEAD; location < mech.locations(); location++) {
            for (int slot = 0; slot < mech.getNumberOfCriticals(location); slot++) {
                CriticalSlot cs = mech.getCritical(location, slot);
                if ((cs != null) && cs.isArmored()) {
                    // Armored cockpit (including command console) adds 1 ton, regardless of number of slots
                    if ((cs.getType() == CriticalSlot.TYPE_SYSTEM)
                            && (cs.getIndex() == Mek.SYSTEM_COCKPIT)) {
                        cockpitWeight = 1.0;
                    } else {
                        weight += 0.5;
                    }
                }
            }
        }
        return weight + cockpitWeight;
    }

    /**
     * Check if the unit has combinations of equipment which are not allowed in
     * the construction rules.
     *
     * @param buff
     *            diagnostics are appended to this
     * @return true if the entity is illegal
     */
    @Override
    public boolean hasIllegalEquipmentCombinations(StringBuffer buff) {
        boolean illegal = super.hasIllegalEquipmentCombinations(buff);

        boolean hasStealth = mech.hasStealth();
        boolean hasC3 = mech.hasC3();
        boolean hasHarjelII = false;
        boolean hasHarjelIII = false;
        boolean hasNullSig = false;
        boolean hasVoidSig = false;
        boolean hasTC = false;
        boolean hasMASC = false;
        boolean hasAES = false;
        boolean hasMechJumpBooster = false;
        boolean hasPartialWing = false;
        EquipmentType advancedMyomer = null;

        //First we find all the equipment that is required or incompatible with other equipment,
        //so we don't have to execute another loop each time one of those situations comes up.
        for (Mounted m : mech.getMisc()) {
            hasHarjelII |= m.getType().hasFlag(MiscType.F_HARJEL_II);
            hasHarjelIII |= m.getType().hasFlag(MiscType.F_HARJEL_III);
            hasNullSig |= m.getType().hasFlag(MiscType.F_NULLSIG);
            hasVoidSig |= m.getType().hasFlag(MiscType.F_VOIDSIG);
            hasTC |= m.getType().hasFlag(MiscType.F_TARGCOMP);
            hasMASC |= m.getType().hasFlag(MiscType.F_MASC)
                    && !m.getType().hasSubType(MiscType.S_SUPERCHARGER);
            hasAES |= m.getType().hasFlag(MiscType.F_ACTUATOR_ENHANCEMENT_SYSTEM);
            if (m.getType().hasFlag(MiscType.F_TSM)
                    || m.getType().hasFlag(MiscType.F_INDUSTRIAL_TSM)
                    || m.getType().hasFlag(MiscType.F_SCM)) {
                advancedMyomer = m.getType();
            }
            hasMechJumpBooster |= m.is(EquipmentTypeLookup.MECH_JUMP_BOOSTER);
            hasPartialWing |= m.getType().hasFlag(MiscType.F_PARTIAL_WING);
        }

        for (Mounted m : getEntity().getMisc()) {
            final MiscType misc = (MiscType) m.getType();

            if (misc.hasFlag(MiscType.F_UMU) && (mech.getJumpType() != Mek.JUMP_NONE)
                    && (mech.getJumpType() != Mek.JUMP_BOOSTER)) {
                illegal = true;
                buff.append("UMUs cannot be mounted with jump jets "
                        + "(jump boosters are legal)\n");
            }

            if (misc.hasFlag(MiscType.F_MASC)
                    && misc.hasSubType(MiscType.S_SUPERCHARGER)) {
                if (mech instanceof LandAirMech) {
                    buff.append("LAMs may not mount a supercharger\n");
                    illegal = true;
                }
            }

            if ((misc.hasFlag(MiscType.F_TSM)
                    || misc.hasFlag(MiscType.F_INDUSTRIAL_TSM)
                    || misc.hasFlag(MiscType.F_SCM))
                    && (misc != advancedMyomer)) {
                buff.append("Cannot mount more than one type of myomer.\n");
                illegal = true;
            }

            if (misc.hasFlag(MiscType.F_REMOTE_DRONE_COMMAND_CONSOLE)) {
                if (mech.getCockpitType() == Mek.COCKPIT_COMMAND_CONSOLE) {
                    buff.append("cockpit command console can't be combined with remote drone command console\n");
                    illegal = true;
                }
            }

            if (misc.hasFlag(MiscType.F_CHAMELEON_SHIELD)) {
                if (hasStealth) {
                    buff.append("Unit mounts both chameleon-light-polarization-system and stealth armor\n");
                    illegal = true;
                }
                if (hasVoidSig) {
                    buff.append("Unit mounts both void-signature-system and a chameleon light polarisation shield\n");
                    illegal = true;
                }
            }

            if (misc.hasFlag(MiscType.F_LIGHT_FLUID_SUCTION_SYSTEM)
                    && !mech.isIndustrial()) {
                illegal = true;
                buff.append("BattleMek can't mount light fluid suction system\n");
            }

            if (!mech.entityIsQuad()) {
                if (replacesHandActuator(misc)
                        && mech.hasSystem(Mek.ACTUATOR_HAND, m.getLocation())) {
                    illegal = true;
                    buff.append("Mech can only mount ").append(misc.getName())
                            .append(" in arm with no hand actuator.\n");
                } else if (replacesLowerArm(misc)
                            && mech.hasSystem(Mek.ACTUATOR_LOWER_ARM, m.getLocation())) {
                        illegal = true;
                        buff.append("Mech can only mount ").append(misc.getName())
                                .append(" in arm with no lower arm actuator.\n");
                } else if (requiresHandActuator(misc) && (m.getLocation() != Entity.LOC_NONE) && !mech.hasSystem(Mek.ACTUATOR_HAND, m.getLocation())) {
                    illegal = true;
                    buff.append("Mech requires a hand actuator in the arm that mounts ").append(misc.getName()).append("\n");
                } else if (requiresLowerArm(misc) && (m.getLocation() != Entity.LOC_NONE) && !mech.hasSystem(Mek.ACTUATOR_LOWER_ARM, m.getLocation())) {
                    illegal = true;
                    buff.append("Mech requires a lower arm actuator in the arm that mounts ").append(misc.getName()).append("\n");
                }
                if (replacesHandActuator(misc) && (m.getLocation() != Entity.LOC_NONE)) {
                    String errorMsg = "Can only mount a single equipment item in the "
                            + mech.getLocationName(m.getLocation())
                            + " that replaces the hand actuator\n";
                    // This error message would appear once for each offending equipment; instead, add it only once
                    if (!buff.toString().contains(errorMsg)) {
                        int miscId = mech.getEquipmentNum(m);
                        if (mech.getMisc().stream().filter(otherMisc -> mech.getEquipmentNum(otherMisc) != miscId)
                                .filter(otherMisc -> otherMisc.getLocation() == m.getLocation())
                                .anyMatch(otherMisc -> replacesHandActuator((MiscType) otherMisc.getType()))) {
                            illegal = true;
                            buff.append(errorMsg);
                        }
                    }
                }
            }
            if (misc.hasFlag(MiscType.F_HEAD_TURRET)
                    && isCockpitLocation(Mek.LOC_HEAD)) {
                illegal = true;
                buff.append("head turret requires torso mounted cockpit\n");
            }

            if (misc.hasFlag(MiscType.F_SHOULDER_TURRET) && mech instanceof QuadMek) {
                illegal = true;
                buff.append("quad mechs can't mount shoulder turrets\n");
            }

            if (misc.hasFlag(MiscType.F_SHOULDER_TURRET)) {
                if (m.getLocation() != Mek.LOC_RT
                        && m.getLocation() != Mek.LOC_LT) {
                    if (mech.countWorkingMisc(MiscType.F_SHOULDER_TURRET, m.getLocation()) > 1) {
                        illegal = true;
                        buff.append("max of 1 shoulder turret per side torso\n");
                    }
                }
            }

            if (misc.hasFlag(MiscType.F_TRACKS)) {
                if (mech instanceof QuadVee) {
                    if (misc.hasSubType(MiscType.S_QUADVEE_WHEELS)
                            != (((QuadVee) mech).getMotiveType() == QuadVee.MOTIVE_WHEEL)) {
                        illegal = true;
                        buff.append("Motive equipment does not match QuadVee motive type.\n");
                    }
                } else if (misc.hasSubType(MiscType.S_QUADVEE_WHEELS)) {
                    illegal = true;
                    buff.append("Wheels can only be used on QuadVees.\n");
                }
                for (int loc = 0; loc < mech.locations(); loc++) {
                    if (mech.locationIsLeg(loc)
                            && countCriticalSlotsFromEquipInLocation(mech, m, loc) != 1) {
                        illegal = true;
                        buff.append(misc.getName()).append(" require one critical slot in each leg.\n");
                        break;
                    }
                }
            }

            if (m.getType().hasFlag(MiscType.F_TALON)) {
                int slots = getMech().isSuperHeavy() ? 1 : 2;
                for (int loc = 0; loc < mech.locations(); loc++) {
                    if (mech.locationIsLeg(loc) && countCriticalSlotsFromEquipInLocation(mech, m, loc) != slots) {
                        illegal = true;
                        buff.append("Talons require ").append(slots).append(" critical slots in each leg.\n");
                        break;
                    }
                }
            }

            if (misc.hasFlag(MiscType.F_RAM_PLATE)) {
                if (!(mech instanceof QuadMek)) {
                    buff.append(misc.getName()).append(" can only be mounted on a quad mech.\n");
                    illegal = true;
                }
                if (!mech.hasReinforcedStructure()) {
                    buff.append(misc.getName()).append(" requires reinforced structure.\n");
                    illegal = true;
                }
                for (int loc = 0; loc < mech.locations(); loc++) {
                    if (mech.locationIsTorso(loc)
                            && countCriticalSlotsFromEquipInLocation(mech, m, loc) != 1) {
                        illegal = true;
                        buff.append(misc.getName()).append(" requires one critical slot in each torso location.\n");
                        break;
                    }
                }
            }

            if (mech.isSuperHeavy()
                    && (misc.hasFlag(MiscType.F_TSM)
                            || misc.hasFlag(MiscType.F_INDUSTRIAL_TSM)
                            || misc.hasFlag(MiscType.F_SCM)
                            || misc.hasFlag(MiscType.F_MASC)
                            || misc.hasFlag(MiscType.F_JUMP_JET)
                            || misc.hasFlag(MiscType.F_MECHANICAL_JUMP_BOOSTER)
                            || misc.hasFlag(MiscType.F_UMU)
                            || misc.hasFlag(MiscType.F_ACTUATOR_ENHANCEMENT_SYSTEM)
                            || misc.hasFlag(MiscType.F_MODULAR_ARMOR)
                            || misc.hasFlag(MiscType.F_PARTIAL_WING))) {
                buff.append("Superheavy may not mount ").append(m.getType().getName()).append("\n");
                illegal = true;
            }

            if (mech.isIndustrial()) {
                if (misc.hasFlag(MiscType.F_TSM)
                        || misc.hasFlag(MiscType.F_SCM)
                        || (misc.hasFlag(MiscType.F_MASC) && !misc.hasSubType(MiscType.S_SUPERCHARGER))) {
                    buff.append("industrial mech can't mount ").append(misc.getName()).append("\n");
                    illegal = true;
                }
                if (!mech.hasAdvancedFireControl()
                    && (misc.hasFlag(MiscType.F_TARGCOMP)
                        || misc.hasFlag(MiscType.F_ARTEMIS)
                        || misc.hasFlag(MiscType.F_ARTEMIS_PROTO)
                        || misc.hasFlag(MiscType.F_ARTEMIS_V)
                        || misc.hasFlag(MiscType.F_BAP))) {
                    buff.append("Industrial mech without advanced fire control can't mount ")
                            .append(misc.getName()).append("\n");
                    illegal = true;
                }
            } else {
                if (misc.hasFlag(MiscType.F_INDUSTRIAL_TSM)
                        || misc.hasFlag(MiscType.F_ENVIRONMENTAL_SEALING)
                        || misc.hasFlag(MiscType.F_FUEL)) {
                    buff.append("Non-industrial mech can't mount ").append(misc.getName()).append("\n");
                    illegal = true;
                }
            }

            if ((mech instanceof LandAirMech)
                    && (misc.hasFlag(MiscType.F_MODULAR_ARMOR)
                            || misc.hasFlag(MiscType.F_JUMP_BOOSTER)
                            || misc.hasFlag(MiscType.F_PARTIAL_WING)
                            || misc.hasFlag(MiscType.F_DUMPER)
                            || misc.hasFlag(MiscType.F_HEAVY_BRIDGE_LAYER)
                            || misc.hasFlag(MiscType.F_MEDIUM_BRIDGE_LAYER)
                            || misc.hasFlag(MiscType.F_LIGHT_BRIDGE_LAYER)
                            || (misc.hasFlag(MiscType.F_CLUB)
                                    && (misc.getSubType() == MiscType.S_BACKHOE)
                                    || (misc.getSubType() == MiscType.S_COMBINE)))) {
                buff.append("LAMs may not mount ").append(misc.getName()).append("\n");
                illegal = true;
            }
        }

        if (mech.isSuperHeavy()) {
            if (mech.getGyroType() != Mek.GYRO_SUPERHEAVY) {
                buff.append("Superheavy Mechs must use a superheavy gyro.\n");
                illegal = true;
            }

            if (mech.getArmoredComponentBV() > 0) {
                buff.append("Superheavy Mechs cannot have armored components\n");
                illegal = true;
            }

            if (mech instanceof QuadVee) {
                buff.append("QuadVees cannot be constructed as superheavies.\n");
            }
        } else if (mech.getGyroType() == Mek.GYRO_SUPERHEAVY) {
            buff.append("Only superheavy Mechs can use a superheavy gyro.\n");
            illegal = true;
        }

        if (mech.isIndustrial()) {
            if ((mech.getCockpitType() == Mek.COCKPIT_INDUSTRIAL
                    || mech.getCockpitType() == Mek.COCKPIT_PRIMITIVE_INDUSTRIAL) && hasC3) {
                buff.append("industrial mech without advanced fire control can't use c3 computer\n");
                illegal = true;
            }
            if ((mech.getJumpType() != Mek.JUMP_STANDARD)
                    && (mech.getJumpType() != Mek.JUMP_NONE)
                    && (mech.getJumpType() != Mek.JUMP_PROTOTYPE)
                    && (mech.getJumpType() != Mek.JUMP_BOOSTER)) {
                buff.append("industrial mechs can only mount standard jump jets or mechanical jump boosters\n");
                illegal = true;
            }
            if ((mech.getGyroType() != Mek.GYRO_STANDARD) && (mech.getGyroType() != Mek.GYRO_SUPERHEAVY)) {
                buff.append("industrial mechs can only mount standard gyros\n");
                illegal = true;
            }
            if (hasDoubleHeatSinks()) {
                buff.append("Industrial Meks cannot mount double heat sinks\n");
                illegal = true;
            }
            switch (mech.hasEngine() ? engine.getEngineType() : Engine.NONE) {
                case Engine.NORMAL_ENGINE:
                    break;
                case Engine.COMBUSTION_ENGINE:
                case Engine.FUEL_CELL:
                case Engine.FISSION:
                    if (mech.isSuperHeavy()) {
                        buff.append("Superheavy Industrial Meks can only use standard or large fusion engines\n");
                        illegal = true;
                    }
                    break;
                default:
                    buff.append("Industrial Meks can only use standard and large fusion engines, ICEs, fuel cells or fission\n");
                    illegal = true;
                    break;
            }
        }

        if (mech.isPrimitive()) {
            if (mech.isOmni()) {
                buff.append("primitive mechs can't be omnis\n");
                illegal = true;
            }
            if (!((mech.getStructureType() == EquipmentType.T_STRUCTURE_STANDARD) || (mech
                    .getStructureType() == EquipmentType.T_STRUCTURE_INDUSTRIAL))) {
                buff.append("primitive mechs can't mount advanced inner structure\n");
                illegal = true;
            }
            if (mech.hasEngine() && ((mech.getEngine().getEngineType() == Engine.XL_ENGINE)
                    || (mech.getEngine().getEngineType() == Engine.LIGHT_ENGINE)
                    || (mech.getEngine().getEngineType() == Engine.COMPACT_ENGINE)
                    || mech.getEngine().hasFlag(Engine.LARGE_ENGINE)
                    || (mech.getEngine().getEngineType() == Engine.XXL_ENGINE))) {
                buff.append("primitive mechs can't mount XL, Light, Compact, XXL or Large Engines\n");
                illegal = true;
            }
            if (advancedMyomer != null) {
                buff.append("primitive mechs can't mount advanced myomers\n");
                illegal = true;
            }
            if (mech.isIndustrial()) {
                if (mech.getArmorType(0) != EquipmentType.T_ARMOR_COMMERCIAL) {
                    buff.append("primitive industrialmechs must mount commercial armor\n");
                    illegal = true;
                }
            } else {
                if ((mech.getArmorType(0) != EquipmentType.T_ARMOR_PRIMITIVE)
                        && (mech.getArmorType(0) != EquipmentType.T_ARMOR_INDUSTRIAL)) {
                    buff.append("primitive battlemechs must mount primitive battlemech armor\n");
                    illegal = true;
                }
            }
        }

        if (mech instanceof LandAirMech) {
            if (mech.isOmni()) {
                buff.append("LAMs may not be constructed as omnis\n");
                illegal = true;
            }
            if (mech.getWeight() > 55) {
                buff.append("LAMs cannot be larger than 55 tons.\n");
                illegal = true;
            }
            EquipmentType structure = EquipmentType.get(EquipmentType.getStructureTypeName(mech.getStructureType(),
                    mech.isClan()));
            if (structure.getCriticals(mech) > 0) {
                buff.append("LAMs may not use ").append(structure.getName()).append("\n");
                illegal = true;
            }

            Set<Integer> ats = new HashSet<>();
            for (int i = 0; i < mech.locations(); i++) {
                ats.add(mech.getArmorType(i));
            }
            for (int at : ats) {
                if (at == EquipmentType.T_ARMOR_HARDENED) {
                    buff.append("LAMs cannot use hardened armor.\n");
                    illegal = true;
                } else {
                    final EquipmentType eq = EquipmentType.get(EquipmentType.getArmorTypeName(at, mech.isClan()));
                    if (eq != null && eq.getCriticals(mech) > 0) {
                        buff.append("LAMs cannot use ").append(eq.getName()).append("\n");
                        illegal = true;
                    }
                }
            }
            if (mech.countWorkingMisc(MiscType.F_BOMB_BAY) > 20) {
                buff.append("A LAM has a maximum of 20 bomb bays.\n");
                illegal = true;
            }
            if (isCockpitLocation(Mek.LOC_CT)) {
                buff.append("LAMs may not use torso-mounted cockpits.\n");
                illegal = true;
            }
            if (mech.getNumberOfCriticals(CriticalSlot.TYPE_SYSTEM, Mek.SYSTEM_COCKPIT, Mek.LOC_HEAD) > 1) {
                buff.append("LAMs may not cockpits that require multiple critical slots.\n");
                illegal = true;
            }
            if (mech.getCockpitType() == Mek.COCKPIT_PRIMITIVE
                    || mech.getCockpitType() == Mek.COCKPIT_PRIMITIVE_INDUSTRIAL) {
                illegal = true;
            }
            if (mech.getGyroType() != Mek.GYRO_STANDARD
                    && mech.getGyroType() != Mek.GYRO_COMPACT
                    && mech.getGyroType() != Mek.GYRO_HEAVY_DUTY) {
                buff.append("LAMs may not use ").append(Mek.getGyroDisplayString(mech.getGyroType()))
                .append("\n");
                illegal = true;
            }
            if (mech.getEngine().getEngineType() != Engine.NORMAL_ENGINE
                    && mech.getEngine().getEngineType() != Engine.COMPACT_ENGINE) {
                buff.append("LAMs may only use standard or compact fusion engines.\n");
                illegal = true;
            }

            Map<EquipmentType,Set<Integer>> spread = new HashMap<>();
            for (Mounted m : mech.getEquipment()) {
                if (m.isSplit()) {
                    buff.append("Cannot split ").append(m.getType().getName())
                        .append(" between locations");
                    illegal = true;
                } else if (m.getType() instanceof ArtilleryWeapon) {
                    buff.append("LAMs cannot mount artillery weapons.\n");
                    illegal = true;
                } else if (m.getType() instanceof WeaponType
                        && ((((WeaponType) m.getType()).getAmmoType() == AmmoType.T_GAUSS_HEAVY)
                                || (((WeaponType) m.getType()).getAmmoType() == AmmoType.T_IGAUSS_HEAVY))) {
                    buff.append("LAMs cannot mount heavy gauss rifles.\n");
                    illegal = true;
                } else if ((m.getType() instanceof MiscType)
                        && m.getType().hasFlag(MiscType.F_CLUB)) {
                    buff.append("LAMs cannot be constructed with physical weapons.\n");
                    illegal = true;
                } else if (m.getType().isSpreadable()) {
                    if (!spread.containsKey(m.getType())) {
                        spread.put(m.getType(), new HashSet<>());
                    }
                    spread.get(m.getType()).add(m.getLocation());
                }
            }
            for (EquipmentType et : spread.keySet()) {
                if (spread.get(et).size() > 1) {
                    buff.append(et.getName()).append(" must be allocated to a single location.\n");
                    illegal = true;
                }
            }
            if (!mech.hasSystem(Mek.ACTUATOR_LOWER_ARM, Mek.LOC_RARM)
                    || !mech.hasSystem(Mek.ACTUATOR_LOWER_ARM, Mek.LOC_LARM)
                    || !mech.hasSystem(Mek.ACTUATOR_UPPER_ARM, Mek.LOC_RARM)
                    || !mech.hasSystem(Mek.ACTUATOR_UPPER_ARM, Mek.LOC_LARM)) {
                buff.append("LAMs require upper and lower arm actuators in both arms.\n");
                illegal = true;
            }
        }

        //Make sure all base chassis heat sinks are allocated
        if (mech.isOmni()) {
            int total = 0;
            int allocated = 0;
            boolean compact = false;
            for (Mounted m : mech.getMisc()) {
                if (m.getType().hasFlag(MiscType.F_HEAT_SINK)
                            || m.getType().hasFlag(MiscType.F_DOUBLE_HEAT_SINK)
                            || m.getType().hasFlag(MiscType.F_IS_DOUBLE_HEAT_SINK_PROTOTYPE)) {
                    total++;
                    compact |= m.getType().hasFlag(MiscType.F_COMPACT_HEAT_SINK);
                    if (m.getLocation() != Entity.LOC_NONE) {
                        allocated++;
                    }
                }
            }
            int required = total - (mech.isOmni()
                    ? mech.getEngine().getBaseChassisHeatSinks(compact)
                    : mech.getEngine().integralHeatSinkCapacity(compact));
            if (allocated < required) {
                illegal = true;
                buff.append("Only " + allocated + " of the required " + required + " heat sinks are allocated to critical slots.");
            }
        }

        if (hasMASC && advancedMyomer != null) {
            buff.append("MASC is incompatible with " + advancedMyomer.getName() + "\n");
            illegal = true;
        }

        if (hasAES) {
            if (hasMASC) {
                buff.append("AES is incompatible with MASC.\n");
                illegal = true;
            }
            if (hasTC) {
                buff.append("AES is incompatible with Targeting computers.\n");
                illegal = true;
            }
            if (advancedMyomer != null) {
                buff.append("AES is incompatible with advanced myomers.\n");
            }
            //Find all locations with an AES and map the number in that location to the location index.
            Map<Integer,Long> byLocation = mech.getMisc().stream()
                    .filter(m -> m.getType().hasFlag(MiscType.F_ACTUATOR_ENHANCEMENT_SYSTEM))
                    .collect(Collectors.groupingBy(Mounted::getLocation, Collectors.counting()));
            boolean multiple = false;
            boolean wrongLocation = false;
            int legCount = 0;
            for (Integer loc : byLocation.keySet()) {
                if (mech.locationIsLeg(loc)) {
                    legCount++;
                } else if (loc == Mek.LOC_HEAD
                        || loc == Mek.LOC_CT
                        || loc == Mek.LOC_LT
                        || loc == Mek.LOC_RT) {
                    wrongLocation = true;
                }
                multiple |= byLocation.get(loc) > 1;
            }
            if (multiple) {
                buff.append("Only one AES can be mounted in a single location.\n");
                illegal = true;
            }
            if (wrongLocation) {
                buff.append("AES can only be mounted in an arm or leg location.\n");
                illegal = true;
            }
            if (legCount > 0) {
                for (int loc = 0; loc < mech.locations(); loc++) {
                    if (mech.locationIsLeg(loc)) {
                        legCount--;
                    }
                }
                if (legCount < 0) {
                    buff.append("If an AES is mounted in a leg, all legs must mount one.\n");
                    illegal = true;
                }
            }
        }

        if (hasNullSig) {
            if (hasStealth) {
                buff.append("Unit mounts both null-signature-system and stealth armor\n");
                illegal = true;
            }
            if (hasTC) {
                buff.append("Unit mounts both null-signature-system and targeting computer\n");
                illegal = true;
            }
            if (hasVoidSig) {
                buff.append("Unit mounts both null-signature-system and void-signature-system\n");
                illegal = true;
            }
            if (hasC3) {
                buff.append("Unit mounts both null-signature-system and a c3 system\n");
                illegal = true;
            }
        }

        if (hasVoidSig) {
            if (hasStealth) {
                buff.append("Unit mounts both void-signature-system and stealth armor\n");
                illegal = true;
            }
            if (hasTC) {
                buff.append("Unit mounts both void-signature-system and targeting computer\n");
                illegal = true;
            }
            if (hasC3) {
                buff.append("Unit mounts both void-signature-system and a c3 system\n");
                illegal = true;
            }
        }

        if (hasHarjelII && hasHarjelIII) {
            illegal = true;
            buff.append("Can't mix HarJel II and HarJel III\n");
        }

        if (hasHarjelII || hasHarjelIII) {
            if (mech.isIndustrial()) {
                buff.append("Cannot mount HarJel repair system on IndustrialMek\n");
                illegal = true;
            }
            for (int loc = 0; loc < mech.locations(); ++loc) {
                int count = 0;
                for (Mounted m : mech.getMisc()) {
                    if ((m.getLocation() == loc)
                        && (m.getType().hasFlag(MiscType.F_HARJEL_II)
                         || m.getType().hasFlag(MiscType.F_HARJEL_III))) {
                        ++count;
                    }
                }
                if (count > 1) {
                    buff.append("Cannot mount multiple HarJel repair systems in a location\n");
                    illegal = true;
                }
                if (count == 1) {
                    int armor = mech.getArmorType(loc);
                    switch (armor) {
                        case EquipmentType.T_ARMOR_STANDARD:
                        case EquipmentType.T_ARMOR_FERRO_FIBROUS:
                        case EquipmentType.T_ARMOR_LIGHT_FERRO:
                        case EquipmentType.T_ARMOR_HEAVY_FERRO:
                        case EquipmentType.T_ARMOR_HEAVY_INDUSTRIAL:
                            // these armors are legal with HarJel
                            break;
                        default:
                            buff.append("Cannot mount HarJel repair system in location with this armor type\n");
                            illegal = true;
                    }
                }
            }
        }

        for (WeaponMounted m : mech.getWeaponList()) {
            if ((m.getType().getAmmoType() == AmmoType.T_GAUSS_HEAVY)
                    || (m.getType().getAmmoType() == AmmoType.T_IGAUSS_HEAVY)) {
                boolean torso = mech.locationIsTorso(m.getLocation());
                if (m.getSecondLocation() != Entity.LOC_NONE) {
                    torso = torso && mech.locationIsTorso(m.getSecondLocation());
                }
                if (!mech.isSuperHeavy() && !torso) {
                    buff.append("Heavy Gauss can only be mounted in a torso location.\n");
                    illegal = true;
                }

                if (m.isMechTurretMounted()) {
                    buff.append("Heavy Gauss cannot be mounted in a turret.\n");
                    illegal = true;
                }
            }
            if ((m.getType().hasFlag(WeaponType.F_TASER))
                    && !(mech.hasEngine() && mech.getEngine().isFusion())) {
                buff.append(m.getType().getName()).append(" needs fusion engine\n");
                illegal = true;
            }
        }

        if (mech.hasFullHeadEject()) {
            if ((mech.getCockpitType() == Mek.COCKPIT_TORSO_MOUNTED)
                    || (mech.getCockpitType() == Mek.COCKPIT_COMMAND_CONSOLE)) {
                buff.append("full head ejection system incompatible with cockpit type\n");
                illegal = true;
            }
        }

        // Test if the crit slots for internal structure match the required crits; Note that this intentionally
        // counts crit slots, not mounted equipment as the latter only works with a fully loaded unit in MML and
        // will make units appear invalid during loading (MML calls UnitUtil.expandUnitMounts() after loading)
        String structureName = EquipmentType.getStructureTypeName(mech.getStructureType(),
                TechConstants.isClan(mech.getStructureTechLevel()));
        EquipmentType structure = EquipmentType.get(structureName);
        int requiredStructureCrits = structure.getCriticals(mech);
        if (mech.getNumberOfCriticals(structure) != requiredStructureCrits) {
            buff.append("The internal structure of this mek is not using the correct number of crit slots\n");
            illegal = true;
        }

        if (hasPartialWing && hasMechJumpBooster) {
            buff.append("Partial wings cannot be combined with any type of jump boosters\n");
            illegal = true;
        }

        return illegal;
    }

    /**
     * @param misc A type of equipment
     * @return     Whether the equipment replaces the hand actuator when mounted in an arm
     */
    public static boolean replacesHandActuator(MiscType misc) {
        return misc.hasFlag(MiscType.F_SALVAGE_ARM)
                || misc.hasFlag(MiscType.F_HAND_WEAPON)
                || (misc.hasFlag(MiscType.F_CLUB)
                    && (misc.hasSubType(MiscType.S_CHAINSAW)
                    || misc.hasSubType(MiscType.S_BACKHOE)
                    || misc.hasSubType(MiscType.S_DUAL_SAW)
                    || misc.hasSubType(MiscType.S_MINING_DRILL)
                    || misc.hasSubType(MiscType.S_ROCK_CUTTER)
                    || misc.hasSubType(MiscType.S_SPOT_WELDER)
                    || misc.hasSubType(MiscType.S_WRECKING_BALL)
                    || misc.hasSubType(MiscType.S_FLAIL)));
    }

    /**
     * @param equipment A type of equipment that can be mounted in a mech arm
     * @return     Whether the equipment requires the hand acutator
     */
    public static boolean requiresHandActuator(EquipmentType equipment) {
        return (equipment instanceof MiscType)
                && equipment.hasFlag(MiscType.F_CLUB)
                && equipment.hasSubType(MiscType.S_CHAIN_WHIP
                | MiscType.S_HATCHET
                | MiscType.S_MACE
                | MiscType.S_SWORD
                | MiscType.S_VIBRO_SMALL
                | MiscType.S_VIBRO_MEDIUM
                | MiscType.S_VIBRO_LARGE);
    }

    /**
     * @param misc A type of equipment that can be mounted in a mech arm
     * @return     Whether the equipment requires the lower arm acutator
     */
    public static boolean requiresLowerArm(MiscType misc) {
        return replacesHandActuator(misc) ||
                (misc.hasFlag(MiscType.F_CLUB)
                && misc.hasSubType(MiscType.S_LANCE | MiscType.S_RETRACTABLE_BLADE));
    }

    /**
     * @param misc A type of equipment that can be mounted in a mech arm
     * @return     Whether the equipment replaces the lower arm and hand actuators
     */
    public static boolean replacesLowerArm(MiscType misc) {
        return misc.hasFlag(MiscType.F_CLUB) && misc.hasSubType(MiscType.S_PILE_DRIVER);
    }

    /**
     * @param mech      The Mech
     * @param eq        The equipment
     * @param location  A location index on the Entity
     * @param buffer    If non-null and the location is invalid, will be appended with an explanation
     * @return          Whether the equipment can be mounted in the location on the Mech
     */
    public static boolean isValidMechLocation(Mek mech, EquipmentType eq, int location, @Nullable StringBuffer buffer) {
        if (eq instanceof MiscType) {
            if (eq.hasFlag(MiscType.F_CLUB) && (eq.hasSubType(MiscType.S_DUAL_SAW | MiscType.S_PILE_DRIVER
                                     | MiscType.S_BACKHOE | MiscType.S_MINING_DRILL
                                    | MiscType.S_COMBINE | MiscType.S_CHAINSAW
                                    | MiscType.S_ROCK_CUTTER | MiscType.S_BUZZSAW | MiscType.S_SPOT_WELDER))) {
                if (mech.entityIsQuad() && (location != Mek.LOC_LT) && (location != Mek.LOC_RT)) {
                    if (buffer != null) {
                        buffer.append(eq.getName()).append(" must be mounted in a side torso.\n");
                    }
                    return false;
                } else if (!mech.entityIsQuad() && (location != Mek.LOC_LARM) && (location != Mek.LOC_RARM)) {
                    if (buffer != null) {
                        buffer.append(eq.getName()).append(" must be mounted in an arm.\n");
                    }
                    return false;
                }
            }
            if (eq.hasFlag(MiscType.F_CLUB) && (eq.hasSubType(MiscType.S_HATCHET | MiscType.S_SWORD
                    | MiscType.S_CHAIN_WHIP | MiscType.S_FLAIL | MiscType.S_LANCE | MiscType.S_WRECKING_BALL
                    | MiscType.S_MACE | MiscType.S_RETRACTABLE_BLADE)
                    || ((MiscType) eq).isShield() || ((MiscType) eq).isVibroblade())
                    && (mech.entityIsQuad() || ((location != Mek.LOC_LARM) && (location != Mek.LOC_RARM)))) {
                if (buffer != null) {
                    buffer.append(eq.getName()).append(" must be mounted in an arm.\n");
                }
                return false;
            }
            if (eq.hasFlag(MiscType.F_HAND_WEAPON) && eq.hasSubType(MiscType.S_CLAW)
                    && (mech.entityIsQuad() || ((location != Mek.LOC_LARM) && (location != Mek.LOC_RARM)))) {
                if (buffer != null) {
                    buffer.append(eq.getName()).append(" must be mounted in an arm.\n");
                }
                return false;
            }
            if (eq.hasFlag(MiscType.F_SALVAGE_ARM) && (mech.entityIsQuad()
                    || ((location != Mek.LOC_LARM) && (location != Mek.LOC_RARM)))) {
                if (buffer != null) {
                    buffer.append(eq.getName()).append(" must be mounted in an arm.\n");
                }
                return false;
            }
            if (eq.hasFlag(MiscType.F_ACTUATOR_ENHANCEMENT_SYSTEM)
                    && ((location == Mek.LOC_HEAD) || mech.locationIsTorso(location))) {
                if (buffer != null) {
                    buffer.append(eq.getName()).append(" must be mounted in an arm or leg location.\n");
                }
                return false;
            }
            if (eq.hasFlag(MiscType.F_HEAD_TURRET) && (location != Mek.LOC_CT)) {
                if (buffer != null) {
                    buffer.append(eq.getName()).append(" must be placed in the center torso.\n");
                }
                return false;
            }
            if ((eq.hasFlag(MiscType.F_QUAD_TURRET) || eq.hasFlag(MiscType.F_SHOULDER_TURRET))
                    && (location != Mek.LOC_RT) && (location != Mek.LOC_LT)) {
                if (buffer != null) {
                    buffer.append(eq.getName()).append(" must be placed in a side torso.\n");
                }
                return false;
            }
            if (eq.hasFlag(MiscType.F_HARJEL) && mech.hasSystem(Mek.SYSTEM_COCKPIT, location)) {
                if (buffer != null) {
                    buffer.append(eq.getName()).append(" cannot be placed in the same location as the cockpit.\n");
                }
                return false;
            }
            if ((eq.hasFlag(MiscType.F_MASS) || eq.hasFlag(MiscType.F_REMOTE_DRONE_COMMAND_CONSOLE))
                    && !mech.hasSystem(Mek.SYSTEM_COCKPIT, location)) {
                if (buffer != null) {
                    buffer.append(eq.getName()).append(" must be placed in the same location as the cockpit.\n");
                }
                return false;
            }
            if (((eq.hasFlag(MiscType.F_MASC) && eq.hasSubType(MiscType.S_SUPERCHARGER))
                    || eq.hasFlag(MiscType.F_EMERGENCY_COOLANT_SYSTEM))
                    && !mech.hasSystem(Mek.SYSTEM_ENGINE, location)) {
                if (buffer != null) {
                    buffer.append(eq.getName()).append(" must be placed in the a location with an engine critical.\n");
                }
                return false;
            }
            if ((eq.hasFlag(MiscType.F_FUEL) || (eq.hasFlag(MiscType.F_CASE) && !eq.isClan())
                    || eq.hasFlag(MiscType.F_LADDER) || eq.hasFlag(MiscType.F_LIGHT_BRIDGE_LAYER)
                    || eq.hasFlag(MiscType.F_MEDIUM_BRIDGE_LAYER) || eq.hasFlag(MiscType.F_HEAVY_BRIDGE_LAYER))
                    && !mech.locationIsTorso(location)) {
                if (buffer != null) {
                    buffer.append(eq.getName()).append(" must be placed in a torso location.\n");
                }
                return false;
            }
            if (eq.hasFlag(MiscType.F_LIFTHOIST) && ((location == Mek.LOC_HEAD) || mech.locationIsLeg(location))) {
                if (buffer != null) {
                    buffer.append(eq.getName()).append(" must be placed in a torso or arm location.\n");
                }
                return false;
            }
            if (eq.hasFlag(MiscType.F_JUMP_JET)
                    && !mech.locationIsLeg(location) && !mech.locationIsTorso(location)) {
                if (buffer != null) {
                    buffer.append(eq.getName()).append(" must be placed in a torso or leg location.\n");
                }
                return false;
            }
            if ((eq.hasFlag(MiscType.F_AP_POD) || eq.hasFlag(MiscType.F_TRACKS) || eq.hasFlag(MiscType.F_TALON))
                    && !mech.locationIsLeg(location)) {
                if (buffer != null) {
                    buffer.append(eq.getName()).append(" must be placed in a leg location.\n");
                }
                return false;
            }
            if (eq.hasFlag(MiscType.F_MODULAR_ARMOR) && (location == Mek.LOC_HEAD)) {
                if (buffer != null) {
                    buffer.append(eq.getName()).append(" cannot be placed in the head.\n");
                }
                return false;
            }
            if (eq.hasFlag(MiscType.F_EJECTION_SEAT) && (location != Mek.LOC_HEAD)) {
                if (buffer != null) {
                    buffer.append(eq.getName()).append(" must be placed in the head.\n");
                }
                return false;
            }
        } else if (eq instanceof WeaponType) {
            if (eq.hasFlag(WeaponType.F_VGL) && !mech.locationIsTorso(location)) {
                if (buffer != null) {
                    buffer.append(eq.getName()).append(" must be placed in a torso location.\n");
                }
                return false;
            }
            if (((((WeaponType) eq).getAmmoType() == AmmoType.T_GAUSS_HEAVY)
                    || ((WeaponType) eq).getAmmoType() == AmmoType.T_IGAUSS_HEAVY)
                    && !mech.isSuperHeavy() && !mech.locationIsTorso(location)) {
                if (buffer != null) {
                    buffer.append(eq.getName()).append(" must be placed in a torso location.\n");
                }
                return false;
            }
        }
        return true;
    }
}
