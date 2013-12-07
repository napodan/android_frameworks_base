/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.animation;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * This class plays a set of {@link Animatable} objects in the specified order. Animations
 * can be set up to play together, in sequence, or after a specified delay.
 *
 * <p>There are two different approaches to adding animations to a <code>Sequencer</code>:
 * either the {@link Sequencer#playTogether(Animatable...) playTogether()} or
 * {@link Sequencer#playSequentially(Animatable...) playSequentially()} methods can be called to add
 * a set of animations all at once, or the {@link Sequencer#play(Animatable)} can be
 * used in conjunction with methods in the {@link android.animation.Sequencer.Builder Builder}
 * class to add animations
 * one by one.</p>
 *
 * <p>It is possible to set up a <code>Sequencer</code> with circular dependencies between
 * its animations. For example, an animation a1 could be set up to start before animation a2, a2
 * before a3, and a3 before a1. The results of this configuration are undefined, but will typically
 * result in none of the affected animations being played. Because of this (and because
 * circular dependencies do not make logical sense anyway), circular dependencies
 * should be avoided, and the dependency flow of animations should only be in one direction.
 */
public final class Sequencer extends Animatable {

    /**
     * Tracks aniamtions currently being played, so that we know what to
     * cancel or end when cancel() or end() is called on this Sequencer
     */
    private final ArrayList<Animatable> mPlayingSet = new ArrayList<Animatable>();

    /**
     * Contains all nodes, mapped to their respective Animatables. When new
     * dependency information is added for an Animatable, we want to add it
     * to a single node representing that Animatable, not create a new Node
     * if one already exists.
     */
    private final HashMap<Animatable, Node> mNodeMap = new HashMap<Animatable, Node>();

    /**
     * Set of all nodes created for this Sequencer. This list is used upon
     * starting the sequencer, and the nodes are placed in sorted order into the
     * sortedNodes collection.
     */
    private final ArrayList<Node> mNodes = new ArrayList<Node>();

    /**
     * The sorted list of nodes. This is the order in which the animations will
     * be played. The details about when exactly they will be played depend
     * on the dependency relationships of the nodes.
     */
    private final ArrayList<Node> mSortedNodes = new ArrayList<Node>();

    /**
     * The set of listeners to be sent events through the life of an animation.
     */
    private ArrayList<AnimatableListener> mListeners = null;

    /**
     * Flag indicating whether the nodes should be sorted prior to playing. This
     * flag allows us to cache the previous sorted nodes so that if the sequence
     * is replayed with no changes, it does not have to re-sort the nodes again.
     */
    private boolean mNeedsSort = true;

    private SequencerAnimatableListener mSequenceListener = null;

    /**
     * Sets up this Sequencer to play all of the supplied animations at the same time.
     *
     * @param sequenceItems The animations that will be started simultaneously.
     */
    public void playTogether(Animatable... sequenceItems) {
        if (sequenceItems != null) {
            mNeedsSort = true;
            Builder builder = play(sequenceItems[0]);
            for (int i = 1; i < sequenceItems.length; ++i) {
                builder.with(sequenceItems[i]);
            }
        }
    }

    /**
     * Sets up this Sequencer to play each of the supplied animations when the
     * previous animation ends.
     *
     * @param sequenceItems The aniamtions that will be started one after another.
     */
    public void playSequentially(Animatable... sequenceItems) {
        if (sequenceItems != null) {
            mNeedsSort = true;
            if (sequenceItems.length == 1) {
                play(sequenceItems[0]);
            } else {
                for (int i = 0; i < sequenceItems.length - 1; ++i) {
                    play(sequenceItems[i]).before(sequenceItems[i+1]);
                }
            }
        }
    }

    /**
     * This method creates a <code>Builder</code> object, which is used to
     * set up playing constraints. This initial <code>play()</code> method
     * tells the <code>Builder</code> the animation that is the dependency for
     * the succeeding commands to the <code>Builder</code>. For example,
     * calling <code>play(a1).with(a2)</code> sets up the Sequence to play
     * <code>a1</code> and <code>a2</code> at the same time,
     * <code>play(a1).before(a2)</code> sets up the Sequence to play
     * <code>a1</code> first, followed by <code>a2</code>, and
     * <code>play(a1).after(a2)</code> sets up the Sequence to play
     * <code>a2</code> first, followed by <code>a1</code>.
     *
     * <p>Note that <code>play()</code> is the only way to tell the
     * <code>Builder</code> the animation upon which the dependency is created,
     * so successive calls to the various functions in <code>Builder</code>
     * will all refer to the initial parameter supplied in <code>play()</code>
     * as the dependency of the other animations. For example, calling
     * <code>play(a1).before(a2).before(a3)</code> will play both <code>a2</code>
     * and <code>a3</code> when a1 ends; it does not set up a dependency between
     * <code>a2</code> and <code>a3</code>.</p>
     *
     * @param anim The animation that is the dependency used in later calls to the
     * methods in the returned <code>Builder</code> object. A null parameter will result
     * in a null <code>Builder</code> return value.
     * @return Builder The object that constructs the sequence based on the dependencies
     * outlined in the calls to <code>play</code> and the other methods in the
     * <code>Builder</code object.
     */
    public Builder play(Animatable anim) {
        if (anim != null) {
            mNeedsSort = true;
            return new Builder(anim);
        }
        return null;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Note that canceling a <code>Sequencer</code> also cancels all of the animations that it is
     * responsible for.</p>
     */
    @SuppressWarnings("unchecked")
    @Override
    public void cancel() {
        if (mListeners != null) {
            ArrayList<AnimatableListener> tmpListeners =
                    (ArrayList<AnimatableListener>) mListeners.clone();
            for (AnimatableListener listener : tmpListeners) {
                listener.onAnimationCancel(this);
            }
        }
        if (mPlayingSet.size() > 0) {
            for (Animatable item : mPlayingSet) {
                item.cancel();
            }
            mPlayingSet.clear();
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Note that ending a <code>Sequencer</code> also ends all of the animations that it is
     * responsible for.</p>
     */
    @Override
    public void end() {
        if (mPlayingSet.size() > 0) {
            for (Animatable item : mPlayingSet) {
                item.end();
            }
            mPlayingSet.clear();
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Starting this <code>Sequencer</code> will, in turn, start the animations for which
     * it is responsible. The details of when exactly those animations are started depends on
     * the dependency relationships that have been set up between the animations.
     */
    @SuppressWarnings("unchecked")
    @Override
    public void start() {
        // First, sort the nodes (if necessary). This will ensure that sortedNodes
        // contains the animation nodes in the correct order.
        sortNodes();

        // nodesToStart holds the list of nodes to be started immediately. We don't want to
        // start the animations in the loop directly because we first need to set up
        // dependencies on all of the nodes. For example, we don't want to start an animation
        // when some other animation also wants to start when the first animation begins.
        ArrayList<Node> nodesToStart = new ArrayList<Node>();
        for (Node node : mSortedNodes) {
            if (mSequenceListener == null) {
                mSequenceListener = new SequencerAnimatableListener(this);
            }
            node.animation.addListener(mSequenceListener);
            if (node.dependencies == null || node.dependencies.size() == 0) {
                nodesToStart.add(node);
            } else {
                for (Dependency dependency : node.dependencies) {
                    dependency.node.animation.addListener(
                            new DependencyListener(node, dependency.rule));
                }
                node.tmpDependencies = (ArrayList<Dependency>) node.dependencies.clone();
            }
        }
        // Now that all dependencies are set up, start the animations that should be started.
        for (Node node : nodesToStart) {
            node.animation.start();
            mPlayingSet.add(node.animation);
        }
        if (mListeners != null) {
            ArrayList<AnimatableListener> tmpListeners =
                    (ArrayList<AnimatableListener>) mListeners.clone();
            for (AnimatableListener listener : tmpListeners) {
                listener.onAnimationStart(this);
            }
        }
    }

    /**
     * This class is the mechanism by which animations are started based on events in other
     * animations. If an animation has multiple dependencies on other animations, then
     * all dependencies must be satisfied before the animation is started.
     */
    private static class DependencyListener implements AnimatableListener {

        // The node upon which the dependency is based.
        private Node mNode;

        // The Dependency rule (WITH or AFTER) that the listener should wait for on
        // the node
        private int mRule;

        public DependencyListener(Node node, int rule) {
            this.mNode = node;
            this.mRule = rule;
        }

        /**
         * If an animation that is being listened for is canceled, then this removes
         * the listener on that animation, to avoid triggering further animations down
         * the line when the animation ends.
         */
        public void onAnimationCancel(Animatable animation) {
            Dependency dependencyToRemove = null;
            for (Dependency dependency : mNode.tmpDependencies) {
                if (dependency.node.animation == animation) {
                    // animation canceled - remove the dependency and listener
                    dependencyToRemove = dependency;
                    animation.removeListener(this);
                    break;
                }
            }
            mNode.tmpDependencies.remove(dependencyToRemove);
        }

        /**
         * An end event is received - see if this is an event we are listening for
         */
        public void onAnimationEnd(Animatable animation) {
            if (mRule == Dependency.AFTER) {
                startIfReady(animation);
            }
        }

        /**
         * Ignore repeat events for now
         */
        public void onAnimationRepeat(Animatable animation) {
        }

        /**
         * A start event is received - see if this is an event we are listening for
         */
        public void onAnimationStart(Animatable animation) {
            if (mRule == Dependency.WITH) {
                startIfReady(animation);
            }
        }

        /**
         * Check whether the event received is one that the node was waiting for.
         * If so, mark it as complete and see whether it's time to start
         * the animation.
         * @param dependencyAnimation the animation that sent the event.
         */
        private void startIfReady(Animatable dependencyAnimation) {
            Dependency dependencyToRemove = null;
            for (Dependency dependency : mNode.tmpDependencies) {
                if (dependency.rule == mRule &&
                        dependency.node.animation == dependencyAnimation) {
                    // rule fired - remove the dependency and listener and check to
                    // see whether it's time to start the animation
                    dependencyToRemove = dependency;
                    dependencyAnimation.removeListener(this);
                    break;
                }
            }
            mNode.tmpDependencies.remove(dependencyToRemove);
            if (mNode.tmpDependencies.size() == 0) {
                // all dependencies satisfied: start the animation
                mNode.animation.start();
            }
        }

    }

    private class SequencerAnimatableListener implements AnimatableListener {

        private Sequencer mSequencer;

        SequencerAnimatableListener(Sequencer sequencer) {
            mSequencer = sequencer;
        }

        public void onAnimationCancel(Animatable animation) {
            if (mPlayingSet.size() == 0) {
                if (mListeners != null) {
                    for (AnimatableListener listener : mListeners) {
                        listener.onAnimationCancel(mSequencer);
                    }
                }
            }
        }

        @SuppressWarnings("unchecked")
        public void onAnimationEnd(Animatable animation) {
            animation.removeListener(this);
            mPlayingSet.remove(animation);
            if (mPlayingSet.size() == 0) {
                // If this was the last child animation to end, then notify listeners that this
                // sequence ended
                if (mListeners != null) {
                    ArrayList<AnimatableListener> tmpListeners =
                            (ArrayList<AnimatableListener>) mListeners.clone();
                    for (AnimatableListener listener : tmpListeners) {
                        listener.onAnimationEnd(mSequencer);
                    }
                }
            }
        }

        // Nothing to do
        public void onAnimationRepeat(Animatable animation) {
        }

        // Nothing to do
        public void onAnimationStart(Animatable animation) {
        }

    }

    /**
     * This method sorts the current set of nodes, if needed. The sort is a simple
     * DependencyGraph sort, which goes like this:
     * - All nodes without dependencies become 'roots'
     * - while roots list is not null
     * -   for each root r
     * -     add r to sorted list
     * -     remove r as a dependency from any other node
     * -   any nodes with no dependencies are added to the roots list
     */
    private void sortNodes() {
        if (mNeedsSort) {
            mSortedNodes.clear();
            ArrayList<Node> roots = new ArrayList<Node>();
            for (Node node : mNodes) {
                if (node.dependencies == null || node.dependencies.size() == 0) {
                    roots.add(node);
                }
            }
            ArrayList<Node> tmpRoots = new ArrayList<Node>();
            while (roots.size() > 0) {
                for (Node root : roots) {
                    mSortedNodes.add(root);
                    if (root.nodeDependents != null) {
                        for (Node node : root.nodeDependents) {
                            node.nodeDependencies.remove(root);
                            if (node.nodeDependencies.size() == 0) {
                                tmpRoots.add(node);
                            }
                        }
                    }
                }
                roots.addAll(tmpRoots);
                tmpRoots.clear();
            }
            mNeedsSort = false;
            if (mSortedNodes.size() != mNodes.size()) {
                throw new IllegalStateException("Circular dependencies cannot exist"
                        + " in Sequencer");
            }
        } else {
            // Doesn't need sorting, but still need to add in the nodeDependencies list
            // because these get removed as the event listeners fire and the dependencies
            // are satisfied
            for (Node node : mNodes) {
                if (node.dependencies != null && node.dependencies.size() > 0) {
                    for (Dependency dependency : node.dependencies) {
                        if (node.nodeDependencies == null) {
                            node.nodeDependencies = new ArrayList<Node>();
                        }
                        if (!node.nodeDependencies.contains(dependency.node)) {
                            node.nodeDependencies.add(dependency.node);
                        }
                    }
                }
            }
        }
    }

    /**
     * Dependency holds information about the node that some other node is
     * dependent upon and the nature of that dependency.
     *
     */
    private static class Dependency {
        static final int WITH = 0; // dependent node must start with this dependency node
        static final int AFTER = 1; // dependent node must start when this dependency node finishes

        // The node that the other node with this Dependency is dependent upon
        public Node node;

        // The nature of the dependency (WITH or AFTER)
        public int rule;

        public Dependency(Node node, int rule) {
            this.node = node;
            this.rule = rule;
        }
    }

    /**
     * A Node is an embodiment of both the Animatable that it wraps as well as
     * any dependencies that are associated with that Animation. This includes
     * both dependencies upon other nodes (in the dependencies list) as
     * well as dependencies of other nodes upon this (in the nodeDependents list).
     */
    private static class Node {
        public Animatable animation;

        /**
         *  These are the dependencies that this node's animation has on other
         *  nodes. For example, if this node's animation should begin with some
         *  other animation ends, then there will be an item in this node's
         *  dependencies list for that other animation's node.
         */
        public ArrayList<Dependency> dependencies = null;

        /**
         * tmpDependencies is a runtime detail. We use the dependencies list for sorting.
         * But we also use the list to keep track of when multiple dependencies are satisfied,
         * but removing each dependency as it is satisfied. We do not want to remove
         * the dependency itself from the list, because we need to retain that information
         * if the sequencer is launched in the future. So we create a copy of the dependency
         * list when the sequencer starts and use this tmpDependencies list to track the
         * list of satisfied dependencies.
         */
        public ArrayList<Dependency> tmpDependencies = null;

        /**
         * nodeDependencies is just a list of the nodes that this Node is dependent upon.
         * This information is used in sortNodes(), to determine when a node is a root.
         */
        public ArrayList<Node> nodeDependencies = null;

        /**
         * nodeDepdendents is the list of nodes that have this node as a dependency. This
         * is a utility field used in sortNodes to facilitate removing this node as a
         * dependency when it is a root node.
         */
        public ArrayList<Node> nodeDependents = null;

        /**
         * Constructs the Node with the animation that it encapsulates. A Node has no
         * dependencies by default; dependencies are added via the addDependency()
         * method.
         *
         * @param animation The animation that the Node encapsulates.
         */
        public Node(Animatable animation) {
            this.animation = animation;
        }

        /**
         * Add a dependency to this Node. The dependency includes information about the
         * node that this node is dependency upon and the nature of the dependency.
         * @param dependency
         */
        public void addDependency(Dependency dependency) {
            if (dependencies == null) {
                dependencies = new ArrayList<Dependency>();
                nodeDependencies = new ArrayList<Node>();
            }
            dependencies.add(dependency);
            if (!nodeDependencies.contains(dependency.node)) {
                nodeDependencies.add(dependency.node);
            }
            Node dependencyNode = dependency.node;
            if (dependencyNode.nodeDependents == null) {
                dependencyNode.nodeDependents = new ArrayList<Node>();
            }
            dependencyNode.nodeDependents.add(this);
        }
    }

    /**
     * The <code>Builder</code> object is a utility class to facilitate adding animations to a
     * <code>Sequencer</code> along with the relationships between the various animations. The
     * intention of the <code>Builder</code> methods, along with the {@link
     * Sequencer#play(Animatable) play()} method of <code>Sequencer</code> is to make it possible to
     * express the dependency relationships of animations in a natural way. Developers can also use
     * the {@link Sequencer#playTogether(Animatable...) playTogether()} and {@link
     * Sequencer#playSequentially(Animatable...) playSequentially()} methods if these suit the need,
     * but it might be easier in some situations to express the sequence of animations in pairs.
     * <p/>
     * <p>The <code>Builder</code> object cannot be constructed directly, but is rather constructed
     * internally via a call to {@link Sequencer#play(Animatable)}.</p>
     * <p/>
     * <p>For example, this sets up a Sequencer to play anim1 and anim2 at the same time, anim3 to
     * play when anim2 finishes, and anim4 to play when anim3 finishes:</p>
     * <pre>
     *     Sequencer s = new Sequencer();
     *     s.play(anim1).with(anim2);
     *     s.play(anim2).before(anim3);
     *     s.play(anim4).after(anim3);
     * </pre>
     * <p/>
     * <p>Note in the example that both {@link Builder#before(Animatable)} and {@link
     * Builder#after(Animatable)} are used. These are just different ways of expressing the same
     * relationship and are provided to make it easier to say things in a way that is more natural,
     * depending on the situation.</p>
     * <p/>
     * <p>It is possible to make several calls into the same <code>Builder</code> object to express
     * multiple relationships. However, note that it is only the animation passed into the initial
     * {@link Sequencer#play(Animatable)} method that is the dependency in any of the successive
     * calls to the <code>Builder</code> object. For example, the following code starts both anim2
     * and anim3 when anim1 ends; there is no direct dependency relationship between anim2 and
     * anim3:
     * <pre>
     *   Sequencer s = new Sequencer();
     *   s.play(anim1).before(anim2).before(anim3);
     * </pre>
     * If the desired result is to play anim1 then anim2 then anim3, this code expresses the
     * relationship correctly:</p>
     * <pre>
     *   Sequencer s = new Sequencer();
     *   s.play(anim1).before(anim2);
     *   s.play(anim2).before(anim3);
     * </pre>
     * <p/>
     * <p>Note that it is possible to express relationships that cannot be resolved and will not
     * result in sensible results. For example, <code>play(anim1).after(anim1)</code> makes no
     * sense. In general, circular dependencies like this one (or more indirect ones where a depends
     * on b, which depends on c, which depends on a) should be avoided. Only create sequences that
     * can boil down to a simple, one-way relationship of animations starting with, before, and
     * after other, different, animations.</p>
     */
    public class Builder {

        /**
         * This tracks the current node being processed. It is supplied to the play() method
         * of Sequencer and passed into the constructor of Builder.
         */
        private Node mCurrentNode;

        /**
         * package-private constructor. Builders are only constructed by Sequencer, when the
         * play() method is called.
         *
         * @param anim The animation that is the dependency for the other animations passed into
         * the other methods of this Builder object.
         */
        Builder(Animatable anim) {
            mCurrentNode = mNodeMap.get(anim);
            if (mCurrentNode == null) {
                mCurrentNode = new Node(anim);
                mNodeMap.put(anim, mCurrentNode);
                mNodes.add(mCurrentNode);
            }
        }

        /**
         * Sets up the given animation to play at the same time as the animation supplied in the
         * {@link Sequencer#play(Animatable)} call that created this <code>Builder</code> object.
         *
         * @param anim The animation that will play when the animation supplied to the
         * {@link Sequencer#play(Animatable)} method starts.
         */
        public void with(Animatable anim) {
            Node node = mNodeMap.get(anim);
            if (node == null) {
                node = new Node(anim);
                mNodeMap.put(anim, node);
                mNodes.add(node);
            }
            Dependency dependency = new Dependency(mCurrentNode, Dependency.WITH);
            node.addDependency(dependency);
        }

        /**
         * Sets up the given animation to play when the animation supplied in the
         * {@link Sequencer#play(Animatable)} call that created this <code>Builder</code> object
         * ends.
         *
         * @param anim The animation that will play when the animation supplied to the
         * {@link Sequencer#play(Animatable)} method ends.
         */
        public void before(Animatable anim) {
            Node node = mNodeMap.get(anim);
            if (node == null) {
                node = new Node(anim);
                mNodeMap.put(anim, node);
                mNodes.add(node);
            }
            Dependency dependency = new Dependency(mCurrentNode, Dependency.AFTER);
            node.addDependency(dependency);
        }

        /**
         * Sets up the given animation to play when the animation supplied in the
         * {@link Sequencer#play(Animatable)} call that created this <code>Builder</code> object
         * to start when the animation supplied in this method call ends.
         *
         * @param anim The animation whose end will cause the animation supplied to the
         * {@link Sequencer#play(Animatable)} method to play.
         */
        public void after(Animatable anim) {
            Node node = mNodeMap.get(anim);
            if (node == null) {
                node = new Node(anim);
                mNodeMap.put(anim, node);
                mNodes.add(node);
            }
            Dependency dependency = new Dependency(node, Dependency.AFTER);
            mCurrentNode.addDependency(dependency);
        }

        /**
         * Sets up the animation supplied in the
         * {@link Sequencer#play(Animatable)} call that created this <code>Builder</code> object
         * to play when the given amount of time elapses.
         *
         * @param delay The number of milliseconds that should elapse before the
         * animation starts.
         */
        public void after(long delay) {
            // setup dummy Animator just to run the clock
            Animator anim = new Animator(delay, 0, 1);
            Node node = new Node(anim);
            mNodes.add(node);
            Dependency dependency = new Dependency(node, Dependency.AFTER);
            mCurrentNode.addDependency(dependency);
        }

    }

}
