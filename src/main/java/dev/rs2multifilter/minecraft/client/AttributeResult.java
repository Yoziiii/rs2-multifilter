package dev.rs2multifilter.minecraft.client;

/** Runtime status only; never serialized into card data. */
public record AttributeResult(State state, String diagnostic) {
    public enum State { MATCH, NO_MATCH, UNAVAILABLE }
    public static AttributeResult match(boolean matches) {
        return new AttributeResult(matches ? State.MATCH : State.NO_MATCH, "");
    }
    public static AttributeResult unavailable(String reason) {
        return new AttributeResult(State.UNAVAILABLE, reason);
    }
    public static String failure(Throwable error) {
        while (error instanceof java.lang.reflect.InvocationTargetException && error.getCause()!=null)
            error=error.getCause();
        return error.getClass().getSimpleName() + (error.getMessage()==null ? "" : ": " + error.getMessage());
    }
    public boolean available() { return state != State.UNAVAILABLE; }
    public boolean matched() { return state == State.MATCH; }
}
