package edu.wpi.cs.bnet;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BNet {

    private static final Pattern parentPattern = Pattern.compile("(\\w+)");
    private static final Pattern cptPattern = Pattern.compile("(\\d\\.\\d+)");
    private static final Pattern inputLinePattern = Pattern.compile("(?<name>\\w+): \\[(?<parents>(?:" + parentPattern.pattern() + " ?)*)\\] \\[(?<cpt>(?:" + cptPattern.pattern() + " ?)+)\\]");

    enum NodeType {
        QUERY("\\?|q"), TRUE("t"), FALSE("f"), UNKNOWN("-");

        private final String pattern;

        NodeType(String pattern) {
            this.pattern = pattern;
        }

        public String getPattern() {
            return pattern;
        }
    }

    public class Node {

        private final String name;
        private NodeType type;
        private final String[] inputNames;
        private Node[] inputs;
        private final double[] cpt;

        public Node(String name, NodeType type, String[] inputNames, double[] cpt) {
            this.name = name;
            this.type = type;
            this.inputNames = inputNames;
            this.inputs = null;
            this.cpt = cpt;
            if (cpt.length != 1 << inputNames.length) {
                throw new IllegalArgumentException("CPT is the wrong size");
            }
        }

        public String getName() {
            return name;
        }

        public NodeType getType() {
            return type;
        }

        public void setType(NodeType type) {
            this.type = type;
        }

        public Node[] getInputs() {
            return inputs;
        }

        public double[] getCPT() {
            return cpt;
        }

        public double cptLookup(Map<Node, Boolean> event) {
            int cptIndex = 0;
            for (int i = 0; i < inputs.length; i++) {
                Node input = inputs[i];
//            if (!event.containsKey(input)) {
                sample(input, event);
//            }
                if (event.get(input)) {
                    cptIndex |= 1 << i;
                }
            }
            return cpt[cptIndex];
        }

        @Override
        public String toString() {
            return "Node{" +
                    "name='" + name + '\'' +
                    ", type=" + type +
                    ", inputNames=" + Arrays.toString(inputNames) +
                    ", cpt=" + Arrays.toString(cpt) +
                    '}';
        }
    }

    private final List<String> nodeNames;
    private final List<Node> nodes;
    private final Set<Node> leafNodes;

    public BNet(InputStream in) throws IOException {
        nodeNames = new ArrayList<>();
        nodes = new ArrayList<>();

        Scanner scanner = new Scanner(in);
        while (scanner.hasNext()) {
            String line = scanner.nextLine().trim();
            Matcher matcher = inputLinePattern.matcher(line);
            if (!matcher.find()) {
                throw new IOException("Bad file format");
            }
            String name = matcher.group("name");
            Set<String> parents = new HashSet<>();
            List<Double> cpt = new ArrayList<>();
            {
                Matcher parentMatcher = parentPattern.matcher(matcher.group("parents"));
                while (parentMatcher.find()) {
                    parents.add(parentMatcher.group());
                }
            }
            {
                Matcher cptMatcher = cptPattern.matcher(matcher.group("cpt"));
                while (cptMatcher.find()) {
                    cpt.add(Double.parseDouble(cptMatcher.group()));
                }
            }
            double[] cptArray = new double[cpt.size()];
            for (int i = 0; i < cpt.size(); i++) {
                cptArray[i] = cpt.get(i);
            }
            nodeNames.add(name);
            nodes.add(new Node(name, NodeType.UNKNOWN,
                    parents.toArray(new String[parents.size()]),
                    cptArray));
        }

        for (Node node : nodes) {
            node.inputs = new Node[node.inputNames.length];
            for (int i = 0; i < node.inputs.length; i++) {
                node.inputs[i] = getNode(node.inputNames[i]);
            }
        }

        leafNodes = nodes.stream()
                .filter(node -> nodes.stream()
                        .filter(node2 -> node2 != node)
                        .noneMatch(node2 -> Stream.of(node2.inputs)
                                .anyMatch(node3 -> node3 == node)))
                .collect(Collectors.toSet());
    }

    public Node getNode(String name) {
        return nodes.get(nodeNames.indexOf(name));
    }

    public List<Node> getNodes() {
        return nodes;
    }

    public Set<Node> getLeafNodes() {
        return leafNodes;
    }

    public Node getQueryNode() {
        return getNodes().stream().filter(node -> node.type == NodeType.QUERY).findAny().get();
    }

    public Stream<Node> streamEvidenceNodes() {
        return getNodes().stream()
                .filter(node -> node.type == NodeType.TRUE || node.type == NodeType.FALSE);
    }

    public Map<Node, Boolean> priorSample() {
        return priorSample(new HashMap<>());
    }

    private Map<Node, Boolean> priorSample(Map<Node, Boolean> event) {
        for (Node node : leafNodes) {
            sample(node, event);
        }
        return event;
    }

    private boolean sample(Node node, Map<Node, Boolean> event) {
        for (Node input : node.inputs) {
            sample(input, event);
        }
        boolean value;
        if (!event.containsKey(node)) {
            value = Math.random() < node.cptLookup(event);
            event.put(node, value);
        }
        else {
            value = event.get(node);
        }
        return value;
    }

    public double rejectionSampling(int n) {
        Node queryNode = getQueryNode();
        int trueCount = 0;
        int consistentCount = 0;
        for (int i = 0; i < n; i++) {
            Map<Node, Boolean> event = priorSample();
            if (streamEvidenceNodes()
                    .allMatch(node -> event.get(node) == (node.type == NodeType.TRUE))) {
                consistentCount++;
                if (event.get(queryNode)) {
                    trueCount++;
                }
            }
        }
        return (double) trueCount / consistentCount;
    }

    public double likelihoodWeightedSampling(int n) {
        Map<Node, Boolean> baseEvent = new HashMap<>();
        streamEvidenceNodes()
                .forEach(node -> baseEvent.put(node, node.type == NodeType.TRUE));

        Node queryNode = getQueryNode();
        double probability = 0;
        double totalProbability = 0;
        for (int i = 0; i < n; i++) {
            Map<Node, Boolean> event = new HashMap<>(baseEvent);
            sample(queryNode, event);
            double weight = streamEvidenceNodes()
                    .mapToDouble(node -> {
                        double p = node.cptLookup(event);
                        if (node.type == NodeType.TRUE) {
                            return p;
                        }
                        else {
                            return 1 - p;
                        }
                    })
                    .reduce(1, (left, right) -> left * right);
            if (event.get(queryNode)) {
                probability += weight;
            }
            totalProbability += weight;
        }
        return probability / totalProbability;
    }

    @Override
    public String toString() {
        return "BNet{" +
                "nodes=" + nodes +
                '}';
    }
}
