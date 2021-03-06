package edu.wpi.cs.bnet;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Scanner;

/**
 * This class runs the program
 *
 * @author Daniel Beckwith, Aditya Nivarthi
 */
public class Main {

    /**
     * Main method.
     *
     * @param args Command line arguments.
     * @throws IOException Bad file format
     */
    public static void main(String[] args) throws IOException {
        String nodeFile = args[0];

        // Read network file and set up network
        BNet bNet = new BNet(new FileInputStream(nodeFile));

        // Read query file and set up network
        {
            String queryFile = args[1];
            Scanner scanner = new Scanner(new FileInputStream(queryFile));
            String line = scanner.nextLine();
            String[] symbols = line.split(",");
            for (int i = 0; i < symbols.length; i++) {
                String symbol = symbols[i];
                for (BNet.NodeType type : BNet.NodeType.values()) {
                    if (symbol.matches(type.getPattern())) {
                        bNet.getNodes().get(i).setType(type);
                        break;
                    }
                }
            }
        }

//        System.out.println("All Nodes");
//        bNet.getNodes().stream().map(Object::toString).forEach(System.out::println);
//        System.out.println("Leaf Nodes");
//        bNet.getLeafNodes().stream().map(Object::toString).forEach(System.out::println);

        int samples = Integer.parseInt(args[2]);

        // Output
        System.out.println("Rejection Sampling");
        System.out.format("Probability of %s with %,d samples: %f%n", bNet.getQueryNode().getName(), samples, bNet.rejectionSampling(samples));

        System.out.println("Likelihood-Weighted Sampling");
        System.out.format("Probability of %s with %,d samples: %f%n", bNet.getQueryNode().getName(), samples, bNet.likelihoodWeightedSampling(samples));
    }
}
