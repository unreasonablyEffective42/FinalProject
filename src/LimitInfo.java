public class LimitInfo {
    private final String approaching;
    private final String target;

    public LimitInfo(String approaching, String target) {
        this.approaching = approaching.trim();
        this.target = target.trim();
    }

    public String getApproaching() {
        return approaching;
    }

    public String getTarget() {
        return target;
    }
}
