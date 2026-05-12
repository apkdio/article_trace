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

    // 添加一个模式串

    public void addKeyword(String keyword) {
        Node current = root;
        for (char c : keyword.toCharArray()) {
            current = current.children.computeIfAbsent(c, k -> new Node());
        }
        current.output = keyword;
        current.outputs.add(keyword);
    }

    // 构建失败指针（BFS）

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
                    // 合并输出（如果失败指针节点也有输出）
                    if (child.fail.output != null) {
                        child.outputs.addAll(child.fail.outputs);
                    }
                }
                // 如果失败节点有输出，继承到当前节点（方便匹配时直接取）
                if (child.fail.output != null) {
                    child.outputs.addAll(child.fail.outputs);
                }
            }
        }
    }

// 搜索文本中所有匹配的关键词

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