package application;

public class SettingsManager {
    private static int freeMinutes = 2;
    private static int chargePerBlock = 100; // in centavos
    private static int minutesPerBlock = 2;
    private static int lockCharge = 100; // default ₱1.00
    
    public static int getLockCharge() {
        return lockCharge;
    }
    public static int getFreeMinutes() {
        return freeMinutes;
    }

    public static int getChargePerBlock() {
        return chargePerBlock;
    }

    public static int getMinutesPerBlock() {
        return minutesPerBlock;
    }

    public static void setLockCharge(int centavos) {
        lockCharge = centavos;
    }
    public static void setFreeMinutes(int minutes) {
        freeMinutes = minutes;
    }

    public static void setChargePerBlock(int centavos) {
        chargePerBlock = centavos;
    }

    public static void setMinutesPerBlock(int minutes) {
        minutesPerBlock = minutes;
    }
}
