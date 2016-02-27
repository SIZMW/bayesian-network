package edu.wpi.cs.bnet;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class represents the bayesian network.
 *
 * @author Daniel Beckwith, Aditya Nivarthi
 */
public class BNet {

    private static final Pattern parentPattern = Pattern.compile("(\\w+)");
    private static final Pattern cptPattern = Pattern.compile("(\\d\\.\\d+)");
    private static final Pattern inputLinePattern = Pattern.compile("(?<name>\\w+): \\[(?<parents>(?:" + parentPattern.pattern() + " ?)*)\\] \\[(?<cpt>(?:" + cptPattern.pattern() + " ?)+)\\]");

    /**
     * This enumerated type represents the types of nodes that can be within the bayesian network.
     */
    enum NodeType {
        QUERY("\\?|q"), TRUE("t"), FALSE("f"), UNKNOWN("-");

        private final String pattern;

        /**
         * Creates a NodeType instance with the specified string pattern.
         *
         * @param pattern The string pattern from the input file that represents this node type.
         */
        NodeType(String pattern) {
            this.pattern = pattern;
        }

        /**
         * Returns the pattern for this node type.
         *
         * @return a {@link String}
         */
        public String getPattern() {
            return pattern;
        }
    }

    /**
     * This class represents a single node within the bayesian network.
     *
     * @author Daniel Beckwith, Aditya Nivarthi
     */
    public class Node {

        private final String name;
        private NodeType type;
        private final String[] inputNames;
        private Node[] inputs;
        private final double[] cpt;

        /**
         * Creates a Node instance for the bayesian network.
         *
         * @param name       The name of the node.
         * @param type       The {@link NodeType} of the node.
         * @param inputNames The names of the nodes that are parents of this node.
         * @param cpt        The conditional probability table for this node.
         */
        public Node(String name, NodeType type, String[] inputNames, double[] cpt) {
            this.name = name;
            this.type = type;
            this.inputNames = inputNames;
            this.inputs = null;
            this.cpt = cpt;

            // Incorrect cpt size
            if (cpt.length != 1 << inputNames.length) {
                throw new IllegalArgumentException("CPT is the wrong size");
            }
        }

        /**
         * Returns the name of this node.
         *
         * @return a {@link String}
         */
        public String getName() {
            return name;
        }

        /**
         * Returns the node type of this node.
         *
         * @return a {@link NodeType}
         */
        public NodeType getType() {
            return type;
        }

        /**
         * Sets the node type of this node.
         *
         * @param type The {@link NodeType} to set for this node.
         */
        public void setType(NodeType type) {
            this.type = type;
        }

        /**
         * Returns the parent nodes of this node.
         *
         * @return a Node[]
         */
        public Node[] getInputs() {
            return inputs;
        }

        /**
         * Returns the conditional probability table for this node.
         *
         * @return a double[]
         */
        public double[] getCPT() {
            return cpt;
        }

        /**
         * Returns the probability from the conditional probability table of the specified event.
         *
         * @param event The event to look up in the table.
         * @return a double
         */
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

        /**
         * Returns the string representation of this object.
         *
         * @return a {@link String}
         */
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

    /**
     * Creates a BNet instance with the specified file input.
     *
     * @param in The input stream from the input file.
     * @throws IOException Bad file format
     */
    public BNet(InputStream in) throws IOException {
        nodeNames = new ArrayList<>();
        nodes = new ArrayList<>();

        // Read the input
        Scanner scanner = new Scanner(in);
        while (scanner.hasNext()) {
            String line = scanner.nextLine().trim();
            Matcher matcher = inputLinePattern.matcher(line);

            // Bad file format
            if (!matcher.find()) {
                throw new IOException("Bad file format");
            }

            // Find the name of the node
            String name = matcher.group("name");
            Set<String> parents = new HashSet<>();
            List<Double> cpt = new ArrayList<>();

            // Get parents
            {
                Matcher parentMatcher = parentPattern.matcher(matcher.group("parents"));
                while (parentMatcher.find()) {
                    parents.add(parentMatcher.group());
                }
            }

            // Get cpt table
            {
                Matcher cptMatcher = cptPattern.matcher(matcher.group("cpt"));
                while (cptMatcher.find()) {
                    cpt.add(Double.parseDouble(cptMatcher.group()));
                }
            }

            // Copy cpt values
            double[] cptArray = new double[cpt.size()];
            for (int i = 0; i < cpt.size(); i++) {
                cptArray[i] = cpt.get(i);
            }

            // Add all nodes and set as unknown state
            nodeNames.add(name);
            nodes.add(new Node(name, NodeType.UNKNOWN,
                    parents.toArray(new String[parents.size()]),
                    cptArray));
        }

        // Set up all the parent connections for each node
        for (Node node : nodes) {
            node.inputs = new Node[node.inputNames.length];
            for (int i = 0; i < node.inputs.length; i++) {
                node.inputs[i] = getNode(node.inputNames[i]);
            }
        }

        // Set the leaf nodes of the network
        leafNodes = nodes.stream()
                .filter(node -> nodes.stream()
                        .filter(node2 -> node2 != node)
                        .noneMatch(node2 -> Stream.of(node2.inputs)
                                .anyMatch(node3 -> node3 == node)))
                .collect(Collectors.toSet());
    }

    /**
     * Returns the node in the bayesian network with the specified name.
     *
     * @param name The name of the node to find in the network.
     * @return a {@link Node}
     */
    public Node getNode(String name) {
        return nodes.get(nodeNames.indexOf(name));
    }

    /**
     * Returns the list of nodes in the bayesian network.
     *
     * @return a {@link List}&lt;{@link Node}&gt;
     */
    public List<Node> getNodes() {
        return nodes;
    }

    /**
     * Returns the list of leaf nodes in the bayesian network.
     *
     * @return a {@link Set}&lt;{@link Node}&gt;
     */
    public Set<Node> getLeafNodes() {
        return leafNodes;
    }

    /**
     * Returns the query node in the bayesian network.
     *
     * @return a {@link Node}
     */
    public Node getQueryNode() {
        return getNodes().stream().filter(node -> node.type == NodeType.QUERY).findAny().get();
    }

    /**
     * Returns a stream of all the evidence nodes in the bayesian network.
     *
     * @return a {@link Stream}&lt;{@link Node}&gt;
     */
    public Stream<Node> streamEvidenceNodes() {
        return getNodes().stream()
                .filter(node -> node.type == NodeType.TRUE || node.type == NodeType.FALSE);
    }

    /**
     * Returns the prior sample from the bayesian network.
     *
     * @return a {@link Map}&lt;{@link Node}, {@link Boolean}&gt;
     */
    public Map<Node, Boolean> priorSample() {
        return priorSample(new HashMap<>());
    }

    /**
     * Returns the prior sample from the bayesian network of the specified event.
     *
     * @return a {@link Map}&lt;{@link Node}, {@link Boolean}&gt;
     */
    private Map<Node, Boolean> priorSample(Map<Node, Boolean> event) {
        // Sample each leaf
        for (Node node : leafNodes) {
            sample(node, event);
        }
        return event;
    }

    /**
     * Returns a sample from the bayesian network of the specified event.
     *
     * @return a double
     */
    private boolean sample(Node node, Map<Node, Boolean> event) {
        // Sample inputs
        for (Node input : node.inputs) {
            sample(input, event);
        }

        // Get value if in event, otherwise generate one
        boolean value;
        if (!event.containsKey(node)) {
            value = Math.random() < node.cptLookup(event);
            event.put(node, value);
        } else {
            value = event.get(node);
        }
        return value;
    }

    /**
     * Returns the estimation from rejection sampling on the bayesian network.
     *
     * @param n The number of counts.
     * @return a double
     */
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

    /**
     * Returns the estimation from likelihood weighted sampling on the bayesian network.
     *
     * @param n The number of counts.
     * @return a double
     */
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
                        } else {
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

    /**
     * Returns a string representation of this object.
     *
     * @return a {@link String}
     */
    @Override
    public String toString() {
        return "BNet{" +
                "nodes=" + nodes +
                '}';
    }
}
