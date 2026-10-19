package com.articleTraceBack.Utils;

import java.util.*;

// Aho-Corasick 自动机（多模式匹配）
public class AhoCorasickUtil {

    // 节点内部类
    private static class Node {
        // 子节点映射（char -> Node）
        Map<Character, Node> children = new HashMap<>();
        // 失败指针
        Node fail = null;
        // 当前节点代表的模式串（若为叶子节点）
        String output = null;
        // 该节点对应的所有输出（用于处理一个节点代表多个模式串的情况，比如 "he" 和 "she" 在 "she" 节点上也包含 "he"）
        List<String> outputs = new ArrayList<>();
    }

    private final Node root = new Node();

    /** 添加一个关键词（模式串） */
    public void addKeyword(String keyword) {
        addKeyword(keyword, keyword);
    }

    /**
     * 添加关键词：匹配走 {@code keyword}（归一化后的形式），命中回显 {@code display}（词表里的原词）。
     * 分开是因为归一化会抹掉空格 / 大小写 / 全角，而站长排查时要按原词回词表里找。
     */
    public void addKeyword(String keyword, String display) {
        Node current = root;
        for (char c : keyword.toCharArray()) {
            current = current.children.computeIfAbsent(c, k -> new Node());
        }
        current.output = keyword;
        current.outputs.add(display);
    }

    /** 构建自动机：BFS 建失败指针，构建后方可匹配 */
    public void build() {
        Queue<Node> queue = new LinkedList<>();
        // 初始化第一层节点，失败指针指向 root
        for (Map.Entry<Character, Node> entry : root.children.entrySet()) {
            Node child = entry.getValue();
            child.fail = root;
            queue.offer(child);
        }

        while (!queue.isEmpty()) {
            Node current = queue.poll();
            for (Map.Entry<Character, Node> entry : current.children.entrySet()) {
                char ch = entry.getKey();
                Node child = entry.getValue();
                queue.offer(child);

                // 寻找当前节点的失败指针
                Node failNode = current.fail;
                while (failNode != null && !failNode.children.containsKey(ch)) {
                    failNode = failNode.fail;
                }
                if (failNode == null) {
                    child.fail = root;
                } else {
                    child.fail = failNode.children.get(ch);
                    // 合并输出：失败链上的输出继承过来，同一个 display 只留一份
                    // （旧实现 addAll 了两遍且不去重，被外层 LinkedHashSet 掩盖）
                    if (child.fail.output != null) {
                        for (String out : child.fail.outputs) {
                            if (!child.outputs.contains(out)) {
                                child.outputs.add(out);
                            }
                        }
                    }
                }
            }
        }
    }

/** 搜索文本中所有匹配的关键词，返回命中位置与词 */
    public List<Match> search(String text) {
        List<Match> matches = new ArrayList<>();
        Node current = root;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            // 沿着失败指针跳转直到找到匹配的边
            while (current != root && !current.children.containsKey(c)) {
                current = current.fail;
            }
            if (current.children.containsKey(c)) {
                current = current.children.get(c);
            } // 否则 current 保持原样（若为 root，则无需跳转）

            // 检查当前节点是否有输出
            if (!current.outputs.isEmpty()) {
                for (String keyword : current.outputs) {
                    matches.add(new Match(i - keyword.length() + 1, keyword));
                }
            }
        }
        return matches;
    }
    // 匹配结果实体
    public static class Match {
        public final int start;
        public final String keyword;

        public Match(int start, String keyword) {
            this.start = start;
            this.keyword = keyword;
        }

        @Override
        public String toString() {
            return String.format("[%d] %s", start, keyword);
        }
    }
}