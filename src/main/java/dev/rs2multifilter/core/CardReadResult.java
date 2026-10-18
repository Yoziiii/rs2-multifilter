package dev.rs2multifilter.core;

/** Lossless inspection; unreadable data must never masquerade as a blank card. */
public record CardReadResult(State state, String raw, FilterCard card, String diagnostic) {
    public enum State { EMPTY, VALID, UNKNOWN, CORRUPT }

    public static CardReadResult inspect(String raw) {
        String original = raw == null ? "" : raw;
        if (original.isEmpty()) return new CardReadResult(State.EMPTY, original, FilterCard.blank(), "");
        try {
            return new CardReadResult(State.VALID, original, CardWireCodec.decode(original), "");
        } catch (CardWireCodec.UnsupportedDataException e) {
            return new CardReadResult(State.UNKNOWN, original, null, e.getMessage());
        } catch (IllegalArgumentException e) {
            return new CardReadResult(State.CORRUPT, original, null, e.getMessage());
        }
    }

    public boolean readable() { return state == State.EMPTY || state == State.VALID; }
    public FilterCard requireCard() {
        if (!readable()) throw new IllegalArgumentException(problem());
        return card;
    }
    public String problem() {
        return (state == State.UNKNOWN ? "未知版本或词条" : "损坏配置") + "：" + diagnostic;
    }

    /** Existing v1/v2 bytes are already within the original decode/network bounds.
     * Do not force migration merely to view, synchronize or copy a valid card. */
    public String viewData() {
        if (readable()) {
            if (card.categories().isEmpty()) return CardWireCodec.encode(card.forEditing());
            return raw;
        }
        // Keep the original on the item even when it cannot fit the existing UI packet.
        return raw.length() <= CardWireCodec.MAX_ENCODED_CHARS ? raw : "";
    }

    public boolean migrationExceedsLimit() {
        if (state != State.VALID || !raw.startsWith("v1:")) return false;
        try { CardWireCodec.encode(card); return false; }
        catch (IllegalArgumentException e) { return true; }
    }
}
