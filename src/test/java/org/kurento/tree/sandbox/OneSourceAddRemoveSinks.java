package org.kurento.tree.sandbox;

import java.util.ArrayList;
import java.util.List;

import org.kurento.tree.server.treemanager.TreeException;
import org.kurento.tree.server.treemanager.TreeManager;

public class OneSourceAddRemoveSinks extends UsageSimulation {

	private static final String NUM_TREE_OPERATIONS_REACHED = "NumTreeOperations reached";
	private int numTreeOperations;
	private int numSinksToAdd;
	private int numSinksToRemove;

	private int numCurrentTreeOperations;

	public OneSourceAddRemoveSinks(int numTreeOperations, int numSinksToAdd,
			int numSinksToRemove) {
		super();
		this.numTreeOperations = numTreeOperations;
		this.numSinksToAdd = numSinksToAdd;
		this.numSinksToRemove = numSinksToRemove;
	}

	public OneSourceAddRemoveSinks() {
		this(100, 5, 2);
	}

	@Override
	public void useTreeManager(TreeManager treeManager) {

		String treeId = treeManager.createTree();
		treeManager.setTreeSource(treeId, "fakeSdp");

		numCurrentTreeOperations = 0;

		List<String> sinks = new ArrayList<String>();
		try {
			while (true) {

				for (int i = 0; i < numSinksToAdd; i++) {
					sinks.add(treeManager.addTreeSink(treeId, "fakeSdp")
							.getId());
					updateTreeOperations();
				}

				for (int i = 0; i < numSinksToRemove; i++) {
					int sinkNumber = (int) (Math.random() * sinks.size());
					String sinkId = sinks.remove(sinkNumber);
					treeManager.removeTreeSink(treeId, sinkId);
				}
			}
		} catch (TreeException e) {
			System.out.println("Reached maximum tree capacity");
		} catch (RuntimeException e) {
			if (e.getMessage().equals(NUM_TREE_OPERATIONS_REACHED)) {
				System.out.println(e.getMessage());
			} else {
				throw e;
			}
		}
	}

	private void updateTreeOperations() {
		numCurrentTreeOperations++;
		if (numCurrentTreeOperations == numTreeOperations) {
			throw new RuntimeException(NUM_TREE_OPERATIONS_REACHED);
		}
	}
}
