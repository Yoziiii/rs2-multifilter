package dev.rs2multifilter.core;

import java.io.*;
import java.util.*;

public final class CardWireCodec {
    private CardWireCodec() {}
    private static final int MAGIC = 0x52534632;
    public static final int MAX_BYTES = 16_000;
    public static final int MAX_ENCODED_CHARS = 22_000;

    public static String encode(FilterCard card) {
        Objects.requireNonNull(card);
        if (card.categories().size() > FilterCard.MAX_PER_CARD) throw new IllegalArgumentException("单张卡最多 64 个分类");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeInt(MAGIC);
                out.writeByte(card.dataVersion());
                out.writeShort(card.categories().size());
                for (FilterCategory category : card.categories()) {
                    out.writeUTF(category.name());
                    out.writeUTF(category.iconItemId());
                    out.writeByte(category.mode().ordinal());
                    out.writeShort(category.rules().size());
                    for (FilterRule rule : category.rules()) {
                        out.writeByte(rule.kind().ordinal());
                        out.writeBoolean(rule.exclude());
                        out.writeUTF(rule.value());
                    }
                }
            }
            if (bytes.size() > MAX_BYTES) throw new IllegalArgumentException("过滤卡配置过大");
            return "v2:" + Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("Serialization failed", e);
        }
    }

    public static FilterCard decode(String encoded) {
        if (encoded == null || encoded.isEmpty()) return FilterCard.blank();
        if (encoded.length() > MAX_ENCODED_CHARS || !(encoded.startsWith("v1:") || encoded.startsWith("v2:"))) {
            throw new IllegalArgumentException("未知格式或数据长度超限");
        }
        try {
            boolean legacy = encoded.startsWith("v1:");
            byte[] bytes = Base64.getDecoder().decode(encoded.substring(3));
            if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("数据长度超限");
            try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
                if (in.readInt() != MAGIC) throw new IllegalArgumentException("不是过滤卡数据");
                int version = in.readUnsignedByte();
                if (version != (legacy ? 1 : FilterCard.VERSION)) throw new IllegalArgumentException("不支持的卡片版本");
                int count = in.readUnsignedShort();
                if (count > FilterCard.MAX_PER_CARD) throw new IllegalArgumentException("分类数量超限");
                List<FilterCategory> categories = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    String name = in.readUTF();
                    String icon = in.readUTF();
                    int modeOrdinal = legacy ? 0 : in.readUnsignedByte();
                    if (modeOrdinal >= FilterCategory.Mode.values().length) throw new IllegalArgumentException("未知模式");
                    int rc = in.readUnsignedShort();
                    if (rc > 256) throw new IllegalArgumentException("规则数量无效");
                    List<FilterRule> rules = new ArrayList<>(rc);
                    for (int j = 0; j < rc; j++) {
                        int kind = in.readUnsignedByte();
                        if (kind >= FilterRule.Kind.values().length) throw new IllegalArgumentException("未知规则类型");
                        boolean exclude = in.readBoolean();
                        rules.add(new FilterRule(FilterRule.Kind.values()[kind], in.readUTF(), exclude));
                    }
                    categories.add(new FilterCategory(name, icon, rules, FilterCategory.Mode.values()[modeOrdinal]));
                }
                if (in.read() != -1) throw new IllegalArgumentException("数据尾部包含多余内容");
                return new FilterCard(FilterCard.VERSION, categories);
            }
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalArgumentException("过滤卡数据无效: " + e.getMessage(), e);
        }
    }
}
