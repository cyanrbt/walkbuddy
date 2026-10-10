package cn.study.window;

public final class AlertPolicy {
    private long last = -3000;
    private String previous = "";
    private long previousAt;
    public boolean shouldAlert(String category, long now) {
        boolean repeated = !category.isEmpty() && category.equals(previous) && now - previousAt <= 1200;
        previous = category; previousAt = now;
        if (repeated && now - last >= 3000) { last = now; return true; }
        return false;
    }
    public void clear() { previous = ""; previousAt = 0; }
}
