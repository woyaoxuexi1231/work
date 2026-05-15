package work.N1javabasic.v1.day9;

// 正确版
class CorrectDCL {
    private static volatile CorrectDCL instance;
    private int[] data;

    private CorrectDCL() { data = new int[1000000]; }

    public static CorrectDCL getInstance() {
        if (instance == null) {
            synchronized (CorrectDCL.class) {
                if (instance == null) {
                    instance = new CorrectDCL();
                }
            }
        }
        return instance;
    }
}


