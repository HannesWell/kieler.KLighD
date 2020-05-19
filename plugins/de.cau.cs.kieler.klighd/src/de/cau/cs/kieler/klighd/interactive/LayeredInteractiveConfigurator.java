/*
 * KIELER - Kiel Integrated Environment for Layout Eclipse RichClient
 *
 * http://rtsys.informatik.uni-kiel.de/kieler
 * 
 * Copyright 2020 by
 * + Kiel University
 *   + Department of Computer Science
 *     + Real-Time and Embedded Systems Group
 * 
 * This code is provided under the terms of the Eclipse Public License (EPL).
 */
package de.cau.cs.kieler.klighd.interactive;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.elk.alg.layered.options.CycleBreakingStrategy;
import org.eclipse.elk.alg.layered.options.LayerConstraint;
import org.eclipse.elk.alg.layered.options.LayeredOptions;
import org.eclipse.elk.alg.layered.options.LayeringStrategy;
import org.eclipse.elk.core.math.KVector;
import org.eclipse.elk.core.options.Direction;
import org.eclipse.elk.graph.ElkConnectableShape;
import org.eclipse.elk.graph.ElkEdge;
import org.eclipse.elk.graph.ElkNode;
import org.eclipse.elk.graph.ElkPort;

/**
 * Provides methods for the @code layered} algorithm to set interactive or non-interactive options
 * for the {@link InteractiveLayoutConfigurator}.
 * 
 * @author sdo
 *
 */
public final class LayeredInteractiveConfigurator {
    /**
     * Constant help with node placement, since nodes should not overlap and the their width and
     * height may not be final.
     */
    public static final int NODE_PLACEMENT_HELPER = 100000;

    private LayeredInteractiveConfigurator() {

    }

    /**
     * Sets the coordinates of the nodes in the graph.
     * 
     * @param root Root of the graph
     */
    public static void setCoordinatesDepthFirst(final ElkNode root) {

        for (ElkNode node : root.getChildren()) {
            if (node.hasProperty(LayeredOptions.LAYERING_LAYER_CONSTRAINT)) {
                LayerConstraint constraint = node.getProperty(LayeredOptions.LAYERING_LAYER_CONSTRAINT);
                node.setProperty(LayeredOptions.LAYERING_LAYER_CONSTRAINT, LayerConstraint.NONE);
                switch (constraint) {
                case FIRST:
                    if (node.getProperty(LayeredOptions.LAYERING_LAYER_CHOICE_CONSTRAINT) == -1) {
                        node.setProperty(LayeredOptions.LAYERING_LAYER_CHOICE_CONSTRAINT, 0);
                    }
                    break;
                case LAST:
                    if (node.getProperty(LayeredOptions.LAYERING_LAYER_CHOICE_CONSTRAINT) == -1) {
                        node.setProperty(LayeredOptions.LAYERING_LAYER_CHOICE_CONSTRAINT, 100000);
                    }
                    break;
                }
            }
        }
        for (ElkNode n : root.getChildren()) {
            if (!n.getChildren().isEmpty()) {
                InteractiveLayoutConfigurator.setRequiredInteractiveOptions(n);
            }
        }
        if (!root.getChildren().isEmpty()) {
            setCoordinates(root);
            setInteractiveStrategies(root);
        }

    }

    /**
     * Sets the coordinates of the nodes in the graph {@code root} according to the set constraints.
     * 
     * @param root The root of the graph that should be layouted.
     */
    private static void setCoordinates(final ElkNode root) {
        List<List<ElkNode>> layers = calcLayerNodes(root.getChildren());
        Direction direction = root.getProperty(LayeredOptions.DIRECTION);
        setCoordinateInLayoutDirection(layers, direction);
        int layerId = 0;
        for (List<ElkNode> layer : layers) {
            if (layer.size() > 0) {
                setCoordinatesOrthogonalToLayoutDirection(layer, layerId, direction);
                layerId++;
            }
        }
        return;
    }

    /**
     * Calculates the layers the {@code nodes} belong to.
     * 
     * @param nodes The nodes of the graph for which the layers should be calculated.
     */
    private static List<List<ElkNode>> calcLayerNodes(final List<ElkNode> nodes) {
        ArrayList<ElkNode> allNodes = new ArrayList<ElkNode>();
        ArrayList<ElkNode> nodesWithLayerConstraint = new ArrayList<ElkNode>();
        // Save the nodes which layer constraint are set in a separate list
        for (ElkNode node : nodes) {
            allNodes.add(node);
            if (node.getProperty(LayeredOptions.LAYERING_LAYER_CHOICE_CONSTRAINT) != -1) {
                nodesWithLayerConstraint.add(node);
            }
        }

        // Calculate layers for nodes without constraints based on their layerID,
        // which is set by the previous layout run.
        List<List<ElkNode>> layerNodes = initialLayers(allNodes);

        // Assign layers to nodes with constraints.
        assignLayersToNodesWithProperty(nodesWithLayerConstraint, layerNodes);

        return layerNodes;
    }

    /**
     * Adds the nodes with a layer constraint to the already assigned layered nodes based on their layer constraint.
     * 
     * @param nodesWithLayerConstraint Nodes with set layer constraint that should be added to the layers.
     * @param layering List that contains the layers with their corresponding nodes.
     */
    private static void assignLayersToNodesWithProperty(
            final List<ElkNode> nodesWithLayerConstraint, final List<List<ElkNode>> layering) {
        // Sort nodes with constraint based on their layer.
        nodesWithLayerConstraint.sort((ElkNode a, ElkNode b) -> {
            return a.getProperty(LayeredOptions.LAYERING_LAYER_CHOICE_CONSTRAINT)
                    - b.getProperty(LayeredOptions.LAYERING_LAYER_CHOICE_CONSTRAINT);
        });

        // Add the nodes with constraints to the their desired layer.

        // diff keeps track of the difference between the layer the node should
        // be in and the layer the node is really in.
        // This way nodes with the same layer constraint go in the same layer
        // although it may not be the layer that is specified by the constraint.
        int diff = 0;
        for (ElkNode node : nodesWithLayerConstraint) {
            int currentLayer = node.getProperty(LayeredOptions.LAYERING_LAYER_CHOICE_CONSTRAINT) - diff;
            if (currentLayer < layering.size()) {
                List<ElkNode> nodesOfLayer = layering.get(currentLayer);
                // Shift nodes to remove in-layer edges.
                shiftOtherNodes(node, currentLayer, layering, true);
                shiftOtherNodes(node, currentLayer, layering, false);
                nodesOfLayer.add(node);
            } else {
                diff = diff + currentLayer - layering.size();
                layering.add(new ArrayList<>(Arrays.asList(node)));
            }
        }
    }

    /**
     * Shifts nodes to the right such that edges in the same layer do not exist.
     * 
     * @param movedNode
     *            The node which connected nodes must be shifted .
     * @param layer
     *            The layer {@code moveNode} is in.
     * @param layerNodes
     *            All existing layers with the containing nodes.
     * @param incoming
     *            Determines if incoming or outgoing edges should be considered. True: incoming
     *            edges.
     */
    private static void shiftOtherNodes(final ElkNode movedNode, final int layer,
            final List<List<ElkNode>> layerNodes, final boolean incoming) {
        List<ElkNode> nodesOfLayer = layerNodes.get(layer);
        // get edges
        List<ElkEdge> edges = new ArrayList<>();
        if (incoming) {
            ElkNode root = movedNode.getParent();
            for (ElkEdge edge : root.getContainedEdges()) {
                for (ElkConnectableShape target : edge.getTargets()) {
                    if (target.equals(movedNode)
                            || (target instanceof ElkPort && target.eContainer().equals(movedNode))) {
                        edges.add(edge);
                    }
                }
            }
        } else {
            ElkNode root = movedNode.getParent();
            for (ElkEdge edge : root.getContainedEdges()) {
                for (ElkConnectableShape target : edge.getSources()) {
                    if (target.equals(movedNode)
                            || (target instanceof ElkPort && target.eContainer().equals(movedNode))) {
                        edges.add(edge);
                    }
                }
            }
        }

        for (ElkEdge edge : edges) {
            // get connected node
            ElkNode node = null;
            if (incoming) {
                if (edge.getSources().get(0) instanceof ElkPort) {
                    node = (ElkNode) edge.getSources().get(0).eContainer();
                } else if (edge.getSources().get(0) instanceof ElkNode) {
                    node = (ElkNode) edge.getSources().get(0);
                }
            } else {
                if (edge.getTargets().get(0) instanceof ElkPort) {
                    node = (ElkNode) edge.getTargets().get(0).eContainer();
                } else if (edge.getTargets().get(0) instanceof ElkNode) {
                    node = (ElkNode) edge.getTargets().get(0);
                }
            }
            // shift node to the next layer
            if (nodesOfLayer.contains(node)) {
                nodesOfLayer.remove(node);
                List<ElkNode> newLayer;
                if (layer + 1 < layerNodes.size()) {
                    newLayer = layerNodes.get(layer + 1);
                    newLayer.add(node);
                    // the connected nodes in the layer the node is shifted to must be shifted too
                    shiftOtherNodes(node, layer + 1, layerNodes, false);
                    shiftOtherNodes(node, layer + 1, layerNodes, true);
                } else {
                    layerNodes.add(new ArrayList<>(Arrays.asList(node)));
                }
            }
        }
    }

    /**
     * Sorts the {@code nodes} in layers based on their layerID.
     * 
     * @param nodes
     *            The nodes of the graph which layers should be calculated.
     */
    private static List<List<ElkNode>> initialLayers(final ArrayList<ElkNode> nodes) {
        // Sort by layerID.
        nodes.sort((ElkNode a, ElkNode b) -> {
            return a.getProperty(LayeredOptions.LAYERING_LAYER_I_D)
                    - b.getProperty(LayeredOptions.LAYERING_LAYER_I_D);
        });

        List<List<ElkNode>> layerNodes = new ArrayList<List<ElkNode>>();
        List<ElkNode> nodesOfLayer = new ArrayList<ElkNode>();
        int currentLayer = -1;
        // Assign nodes to layers.
        for (ElkNode node : nodes) {
            int layer = node.getProperty(LayeredOptions.LAYERING_LAYER_I_D);
            if (layer > currentLayer) {
                // Check if a node is added to a new layer.
                if (!nodesOfLayer.isEmpty()) {
                    layerNodes.add(nodesOfLayer);
                }
                nodesOfLayer = new ArrayList<ElkNode>();
                currentLayer = layer;
            }

            // Nodes with layer constraint should be ignored, since they are added later.
            if (node.getProperty(LayeredOptions.LAYERING_LAYER_CHOICE_CONSTRAINT) == -1) {
                nodesOfLayer.add(node);
            }
        }
        if (!nodesOfLayer.isEmpty()) {
            // Add the last layer nodes.
            layerNodes.add(nodesOfLayer);
        }
        return layerNodes;
    }

    /**
     * Sets the x coordinates of the nodes in {@code layers} according to their layer. The problem
     * is that the height and width of a node are determined by the height and width before the
     * layout with constraints. They are therefore potentially wrong. The placement has to be
     * adjusted for this by a very high spacing between nodes.
     * 
     * @param layers
     *            The layers containing the associated nodes, already sorted regarding layers
     * @param direction
     *            The layout direction
     */
    private static void setCoordinateInLayoutDirection(final List<List<ElkNode>> layers,
            final Direction direction) {
        double position = 0;
        double nextPosition = 0;
        // Assign x (RIGHT, LEFT)/y (UP, DOWN) coordinate such that all nodes get a pseudo position
        // that assigns them to the correct layer if interactive mode is used.
        for (List<ElkNode> nodesOfLayer : layers) {
            for (ElkNode node : nodesOfLayer) {
                switch (direction) {
                case UNDEFINED:
                case RIGHT:
                    node.setX(position);
                    if (position + node.getWidth() / 2 >= nextPosition) {
                        nextPosition = node.getX() + node.getWidth() + NODE_PLACEMENT_HELPER;
                    }
                    break;
                case LEFT:
                    node.setX(position);
                    if (node.getX() <= nextPosition) {
                        nextPosition = node.getX() - NODE_PLACEMENT_HELPER;
                    }
                    break;
                case DOWN:
                    node.setY(position);
                    if (position + node.getHeight() >= nextPosition) {
                        nextPosition = node.getY() + node.getHeight() + NODE_PLACEMENT_HELPER;
                    }
                    break;
                case UP:
                    node.setY(position);
                    if (node.getY() <= nextPosition) {
                        nextPosition = node.getY() - NODE_PLACEMENT_HELPER;
                    }
                    break;
                }
            }
            position = nextPosition;
        }
        return;
    }

    /**
     * Sets the positions of the nodes in their layer.
     * 
     * @param nodesOfLayer The list containing nodes that are in the same layer.
     * @param direction The layout direction.
     */
    private static void setCoordinatesOrthogonalToLayoutDirection(final List<ElkNode> nodesOfLayer,
            final int layerId, final Direction direction) {
        // Separate nodes with and without position constraints.
        List<ElkNode> nodesWithPositionConstraint = new ArrayList<ElkNode>();
        List<ElkNode> nodes = new ArrayList<ElkNode>();
        for (ElkNode node : nodesOfLayer) {
            if (node.getProperty(
                    LayeredOptions.CROSSING_MINIMIZATION_POSITION_CHOICE_CONSTRAINT) != -1) {
                nodesWithPositionConstraint.add(node);
            } else {
                nodes.add(node);
            }
        }

        // Determine the order of the nodes.
        sortNodesInLayer(nodesWithPositionConstraint, nodes, direction);
        // Add the nodes with position constraint at the desired position in their layer.
        for (ElkNode node : nodesWithPositionConstraint) {
            int pos = node
                    .getProperty(LayeredOptions.CROSSING_MINIMIZATION_POSITION_CHOICE_CONSTRAINT);
            if (pos < nodes.size()) {
                nodes.add(pos, node);
            } else {
                nodes.add(node);
            }
        }

        // Set the y/x positions according to the order of the nodes.
        switch (direction) {
        case UNDEFINED:
        case RIGHT:
        case LEFT:
            double yPos = nodes.get(0).getY();
            for (ElkNode node : nodes) {
                node.setProperty(LayeredOptions.POSITION, new KVector(node.getX(), yPos));
                yPos += node.getHeight() + NODE_PLACEMENT_HELPER;
            }
            break;
        case DOWN:
        case UP:
            double xPos = nodes.get(0).getX() + 2 * layerId * NODE_PLACEMENT_HELPER;
            for (ElkNode node : nodes) {
                node.setProperty(LayeredOptions.POSITION, new KVector(xPos, node.getY()));
                xPos += node.getWidth() + NODE_PLACEMENT_HELPER;
            }
            break;
        }
    }

    /**
     * Sorts the {@code nodesWithPositionConstraint} according their position constraint and {@code nodes} according
     * to relevant coordinate (y for left, right, x for up, down).
     * 
     * @param nodesWithPositionConstraint The nodes which position constraint is set.
     * @param nodes The nodes without position constraints.
     * @param direction The layout direction.
     */
    private static void sortNodesInLayer(final List<ElkNode> nodesWithPositionConstraint,
            final List<ElkNode> nodes, final Direction direction) {
        // Sort nodes with constraint by their position constraint.
        nodesWithPositionConstraint.sort((ElkNode a, ElkNode b) -> {
            return a.getProperty(LayeredOptions.CROSSING_MINIMIZATION_POSITION_CHOICE_CONSTRAINT)
                    - b.getProperty(
                            LayeredOptions.CROSSING_MINIMIZATION_POSITION_CHOICE_CONSTRAINT);
        });
        // Sort other nodes based on their y(RIGHT/LEFT)/x(DOWN/UP) coordinate.
        nodes.sort((ElkNode a, ElkNode b) -> {
            switch (direction) {
            case UNDEFINED:
            case RIGHT:
            case LEFT:
                return (int) (a.getY() - b.getY());
            case DOWN:
            case UP:
                return (int) (a.getX() - b.getX());
            }
            return 0;
        });
    }

    /**
     * Sets the (semi) interactive strategies in the phases crossing minimization, layer assignment,
     * cycle breaking for the given parent node.
     * 
     * @param parent The graph which strategies should be set.
     */
    private static void setInteractiveStrategies(final ElkNode parent) {
        parent.setProperty(LayeredOptions.CROSSING_MINIMIZATION_SEMI_INTERACTIVE, true);
        parent.setProperty(LayeredOptions.LAYERING_STRATEGY, LayeringStrategy.INTERACTIVE);
        parent.setProperty(LayeredOptions.CYCLE_BREAKING_STRATEGY, CycleBreakingStrategy.INTERACTIVE);
    }
}
