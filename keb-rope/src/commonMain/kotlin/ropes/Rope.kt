package keb.ropes

import keb.assert
import keb.internal.ArrayStack
import keb.internal.PeekableArrayStack

fun Rope(value: String): Rope {
    val root = ropeNodeOf(value)
    return Rope(root)
}

fun emptyRope(): Rope = Rope(emptyRopeNode())

open class Rope(private val root: RopeNode) {
    init {
        assert { root.isBalanced() }
    }

    //TODO: we can also improve this too keep the tree wide.
    open operator fun plus(other: Rope): Rope {
        // Avoid checking for length == 0, since it might have a cost.
        // Either way, if one of the nodes is empty, createParent() will throw IllegalArgumentException.
        if (other === EmptyRope) return this
        val left = root
        val right = other.root
        val newRope = createParent(left, right)
        return Rope(newRope)
    }

    open val length: Int by lazy { root.length() }

    /**
     * Returns the [Char] at the given [index] or `null` if the [index] is out of bounds of this rope.
     */
    open operator fun get(index: Int): Char? =
        getImpl(
            index = index,
            root = root,
            onOutOfBounds = { return null },
            onElementRetrieved = { _, _, element -> return element }
        )

    open fun indexOf(element: Char): Int {
        var index = 0
        for (leaf in root) {
            for (c in leaf.value) {
                if (c == element) return index
                index++
            }
        }
        return -1
    }

    //TODO: maybe this function better return a sequence?
    open fun collectLeaves(): List<RopeLeaf> = root.map { it.value }

    //TODO: make it extension fun
    fun subRope(range: IntRange): Rope = subRope(range.first, range.last + 1)

    //TODO: make it extension fun
    fun subRope(startIndex: Int): Rope = subRope(startIndex, length)

    // `endIndex` is exclusive
    @Suppress("DuplicatedCode")
    open fun subRope(startIndex: Int, endIndex: Int): Rope {
        checkRangeIndexes(startIndex, endIndex)
        // Fast-path, root is a leaf,
        // call directly subStringLeaf() to retrieve subRope.
        if (root is RopeLeafNode) {
            val newLeaf = root.value.subStringLeaf(startIndex, endIndex)
            return Rope(RopeLeafNode(newLeaf))
        }
        // First, we retrieve left and right bounds (indexes).
        // Then, we subtract all leaves between left and right (exclusive) bounds.
        val leftIterator = SingleElementRopeIterator(root, startIndex)
        if (!leftIterator.hasNext()) throwIndexOutOfBoundsExceptionForStartAndEndIndex(startIndex, endIndex)
        val leftLeaf = leftIterator.currentLeaf // leaf where leftIndex is found
        val leftIndex = leftIterator.currentIndex // index in leaf
        // Since, we create the iterator with `endIndex` exclusive,
        // all other operations can safely include `rightIndex`.
        val rightIterator = SingleElementRopeIterator(root, endIndex - 1)
        if (!rightIterator.hasNext()) throwIndexOutOfBoundsExceptionForStartAndEndIndex(startIndex, endIndex)
        val rightLeaf = rightIterator.currentLeaf // leaf where rightIndex is found
        val rightIndex = rightIterator.currentIndex // index in leaf
        if (leftLeaf === rightLeaf) {
            // We use `rightIndex + 1`, because subStringLeaf() is an `endIndex` exclusive operation.
            val newLeaf = leftLeaf.value.subStringLeaf(leftIndex, rightIndex + 1)
            return Rope(RopeLeafNode(newLeaf))
        }
        // Since we need only leaves between startIndex <= leaf <= endIndex,
        // we only need the first common parent to both leaves to retrieve them all.
        val commonParent = findCommonParent(leftIterator, leftLeaf, rightIterator, rightLeaf)
        val newTree = buildTreeFromStartAndEndIndex(leftIndex, leftLeaf, rightIndex, rightLeaf, commonParent)
        return if (newTree.isEmpty) emptyRope() else Rope(newTree)
    }

    private fun buildTreeFromStartAndEndIndex(
        leftIndex: Int,
        leftLeaf: RopeLeafNode,
        rightIndex: Int,
        rightLeaf: RopeLeafNode,
        parent: RopeNode
    ): RopeNode {
        val leaves = parent.collectLeaves()
        val leftLeafIndex = leaves.indexOf(leftLeaf)
        val rightLeafIndex = leaves.indexOf(rightLeaf)
        val newTree = buildBTree {
            for (i in leftLeafIndex..rightLeafIndex) {
                val child = leaves[i]
                when (i) {
                    leftLeafIndex -> {
                        val newLeaf = child.value.subStringLeaf(leftIndex)
                        add(RopeLeafNode(newLeaf))
                    }

                    rightLeafIndex -> {
                        val newLeaf = child.value.subStringLeaf(0, rightIndex)
                        add(RopeLeafNode(newLeaf))
                    }

                    else -> {
                        // In-between leaves can be added as they are,
                        // since they in range: startIndex < leaf < endIndex.
                        addAll(child.collectLeaves())
                    }
                }
            }
        }
        return newTree
    }

    private fun findCommonParent(
        leftIterator: RopeIteratorWithHistory,
        leftLeafNode: RopeLeafNode,
        rightIterator: RopeIteratorWithHistory,
        rightLeafNode: RopeLeafNode
    ): RopeNode {
        var leftNode: RopeNode = leftLeafNode
        var rightNode: RopeNode = rightLeafNode
        while (true) {
            leftNode = leftIterator.findParent(leftNode) ?: error("unexpected")
            rightNode = rightIterator.findParent(rightNode) ?: error("unexpected")
            if (leftNode === rightNode) return leftNode
        }
    }

    // endIndex exclusive
    open fun removeRange(startIndex: Int, endIndex: Int): Rope {
        if (startIndex == 0) return subRope(endIndex)
        val leftTree = subRope(0, startIndex)
        val rightTree = subRope(endIndex)
        return leftTree + rightTree
    }

    private fun throwIndexOutOfBoundsExceptionForStartAndEndIndex(startIndex: Int, endIndex: Int): Nothing {
        throw IndexOutOfBoundsException("startIndex:$startIndex, endIndex:$endIndex, length:$length")
    }

    open fun deleteAt(index: Int): Rope {
        checkPositionIndex(index)
        val iterator = SingleElementRopeIterator(root, index)
        if (!iterator.hasNext()) throw IndexOutOfBoundsException("index:$index, length:$length")
        val leaf = iterator.currentLeaf // leaf where index is found
        val i = iterator.currentIndex // index in leaf
        val newLeaf = leaf.deleteAt(i)
        val newTree = rebuildTreeCleaningEmptyNodes(leaf, newLeaf, iterator)
        return Rope(newTree)
    }

    private fun rebuildTreeCleaningEmptyNodes(
        oldNode: RopeNode,
        newNode: RopeNode,
        iterator: RopeIteratorWithHistory
    ): RopeNode {
        if (oldNode === root && newNode.isEmpty) return emptyRopeNode()
        var old = oldNode
        var new: RopeNode? = newNode
        while (true) {
            if (new?.isEmpty == true) new = null // mark empty nodes as null to clean them out
            if (new != null) return rebuildTree(old, new, iterator)
            // for non-root nodes, findParent() should always return a parent.
            val parent = iterator.findParent(old) ?: error("unexpected")
            val pos = parent.indexOf(old)
            assert { pos >= 0 } // position should always be positive.
            new = parent.deleteAt(pos)
            old = parent
            if (old === root) {
                return if (new.isEmpty) old else new
            }
        }
    }

    // throws for index -1 && out-of-bounds
    //
    // - find target leafNode and check if it has any more space left
    // - if yes then inserted there and rebuild where necessary.
    // - if not, check if parent (internal node) has any space left for one more child
    // - if yes, then insert child in start and rebuild where necessary
    // - if not, split and merge.
    // - rebuilding creates a new root and replaces old one.
    //TODO: One improvement would be to check for more parents up ahead if we can split them,
    // but at this point it is non-trivial and not worth it time-wise.
    open fun insert(index: Int, element: String): Rope {
        checkPositionIndex(index)
        val iterator = SingleElementRopeIterator(root, index)
        // Try to find the target `index`, since we need to locate
        // it and start adding after that `index`.
        if (!iterator.hasNext()) { //TODO: what im doing here?
            // we allow for inserting on + 1 after last-index, since these are
            // the append() operations.
            if (index != length) throw IndexOutOfBoundsException("index:$index, length:$length")
        }
        val leaf = iterator.currentLeaf // leaf where index is found
        val i = iterator.currentIndex // index in leaf
        // If the leaf which contains the index has enough space for adding
        // the element, create new leaf and rebuild tree.
        if (leaf.weight + element.length <= MAX_SIZE_LEAF) { // fast-path
            val newChild = leaf.add(i, element)
            if (leaf === root) return Rope(newChild)
            val newTree = rebuildTree(leaf, newChild, iterator)
            return Rope(newTree)
        }
        // Add the element into leaf and expand (split and merge) as necessary.
        val newChildren = leaf.expandableAdd(i, element)
        if (leaf === root) {
            val newParent = createParent(newChildren)
            return Rope(newParent)
        }
        // At this point, we should always find a parent, since we are in a leaf
        // and hasNext() returned `true`.
        val parent = iterator.findParent(leaf) ?: error("unexpected")
        val pos = parent.indexOf(leaf)
        // If there is space in the parent, add new leaf/s to keep the tree wide
        // as much as possible.
        if (newChildren.size + parent.children.size - 1 <= MAX_CHILDREN) {
            val newParent = parent.set(pos, newChildren)
            val newTree = rebuildTree(parent, newParent, iterator)
            return Rope(newTree)
        }
        // Replace leaf with new node.
        // Note, at this point, we deepen the tree rather than keep it wide.
        val newChild = merge(newChildren)
        val newParent = parent.set(pos, newChild)
        val newTree = rebuildTree(parent, newParent, iterator)
        return Rope(newTree)
    }

    private fun rebuildTree(
        oldNode: RopeNode,
        newNode: RopeNode,
        iterator: RopeIteratorWithHistory
    ): RopeNode {
        if (oldNode === root) return newNode
        var old = oldNode
        var new = newNode
        while (true) {
            // for non-root nodes, findParent() should always return a parent.
            val parent = iterator.findParent(old) ?: error("unexpected")
            new = parent.replace(old, new)
            old = parent
            if (old === root) return new
        }
    }

    /**
     * Abstract get implementation.
     *
     * It is a variant of binary search whereas we move down the tree,
     * if `index` is less than the weight of the node, we go to the left.
     * If on the other hand `index` is higher we go to the right, subtracting the value of weight from `index`.
     * This way we are able to skip left subtrees if `index` is not in that part of the tree.
     *
     * Note: It uses a stack to keep references to parent nodes, in case it needs to traverse the tree backwards.
     */
    private inline fun <R> getImpl(
        /* The target index to retrieve. */
        index: Int,
        /* The tree which we iterate. */
        root: RopeNode,
        /* The stack which keeps references to parent nodes. */
        stack: ArrayStack<RopeInternalNodeChildrenIterator> = defaultStack(),
        /* This lambda is invoked when the target index is
        out of bounds for the current in tree. */
        onOutOfBounds: () -> R,
        /* This lambda is invoked when the target element has
        been retrieved successfully. */
        onElementRetrieved: (
            leaf: RopeLeafNode,
            i: Int,
            element: Char
        ) -> R,
        /* This lambda is invoked when we retrieve the next
        child-node by a preceding nextChild() call. */
        onNextChild: (next: RopeNode) -> Unit = {}
    ): R {
        if (index < 0) return onOutOfBounds() // rope does not support negative `index`
        var curIndex = index
        var curNode = root
        while (true) {
            when (curNode) {
                is LeafNode -> {
                    if (curIndex < curNode.weight) {
                        return onElementRetrieved(curNode, curIndex, curNode.value[curIndex])
                    }
                    if (curNode === root) return onOutOfBounds() // Single-node btree.
                    // If "curIndex" is higher than the node's weight,
                    // then we subtract from the node's weight.
                    // This way as we move down the tree,
                    // the "curIndex"  decreases,
                    // and once we reach a leafNode
                    // the character at position "curIndex"
                    // is the target.
                    curIndex -= curNode.weight
                    val parent = stack.popOrNull()
                        ?: error("leaf:$curNode does not have a parent in stack")
                    // Iterate the next child and keep `self` reference in stack, since we
                    // need to allow a child to find its parent in stack in the case of "failure".
                    curNode = parent.nextChildAndKeepRefOrElse(stack) {
                        // If neither `parent` nor stack has a node to give back, then there are no more
                        // nodes to traverse. Technically, returning `null` here means we are in rightmost subtree.
                        stack.popOrNull() ?: return onOutOfBounds()
                    }
                    onNextChild(curNode)
                }

                is InternalNode -> {
                    val node = if (curNode is RopeInternalNodeChildrenIterator) {
                        curNode
                    } else {
                        curNode.childrenIterator()
                    }
                    // Push the current node, so we can always return as a fallback.
                    stack.push(node)
                    // If `index` is less than node's weight, then `index` is in this subtree.
                    if (curIndex < node.weight) {
                        curNode = node.nextChildOrElse {
                            // At this point, `index` is out of bounds because we tried to iterate
                            // a non-existent "next" node, in an internal node when we are certain that
                            // `index` should be within this subtree. Technically, this happens because
                            // when we are in the rightmost leafNode, we cannot be sure there is not a
                            // "next" leaf. We have to iterate the tree backwards and check explicitly.
                            return onOutOfBounds()
                        }
                        onNextChild(curNode)
                        continue
                    }
                    if (node.index == 0) { // Leftmost child.
                        // No need to check leaves on leftmost child,
                        // since "curIndex" is higher than node's weight,
                        // and each node holds the sum of the lengths of all the leaves in its left subtree.
                        curIndex -= node.weight
                        if (!node.tryIncIndex()) { // Skip leftmost-child
                            // No more children to traverse in this node, go to the parent node.
                            // If either node is the root or there is no parent, then it means there
                            // are no more nodes to traverse, and `index` is out of bounds.
                            curNode = node.moveStackForward(stack) ?: return onOutOfBounds()
                            onNextChild(curNode)
                            continue
                        }
                    }
                    // Move to the next child node.
                    curNode = node.nextChildOrElse {
                        // If stack returns `null`, there are no more nodes to iterate.
                        // In that case, we can safely assume we are out of bounds.
                        node.moveStackForward(stack) ?: return onOutOfBounds()
                    }
                    onNextChild(curNode)
                }
            }
        }
    }

    /**
     * Moves forward in the specified [stack] until we find the first node that is not [this] node,
     * or returns `null` if [stack] holds no more elements.
     *
     * We check if next node is the same one, because [stack] might hold up a reference to [this] node.
     */
    private fun RopeNode.moveStackForward(
        stack: ArrayStack<RopeInternalNodeChildrenIterator>
    ): RopeInternalNodeChildrenIterator? {
        var stackNode = stack.popOrNull() ?: return null
        while (stackNode === this) {
            stackNode = stack.popOrNull() ?: return null
        }
        return stackNode
    }

    /**
     * Returns an optimal sized-stack, where the size is equal to root's height.
     * In most use-cases, stack will not need to resize.
     */
    private fun defaultStack(): ArrayStack<RopeInternalNodeChildrenIterator> = ArrayStack(root.height)

    fun iteratorWithIndex(startIndex: Int) = RopeIterator(root, startIndex)

    operator fun iterator(): RopeIterator = RopeIterator(root, 0)

    internal interface RopeIteratorWithHistory {
        fun findParent(child: RopeNode): RopeInternalNode?
    }

    open inner class RopeIterator(private val root: RopeNode, startIndex: Int) : RopeIteratorWithHistory {
        init {
            checkPositionIndex(startIndex)
            // This implementation has second `init`.
        }

        private val links = mutableMapOf<RopeNode, RopeInternalNode>() // child || parent
        private val onNextStack = PeekableArrayStack<RopeNode>(root.height)
        private val parentNodesRef = defaultStack()

        init {
            //TODO: add explanation
            onNextStack.push(root)
        }

        private var curIndex = startIndex
        private var nextIndex = curIndex
        private var curNode = root

        val currentIndex get() = curIndex

        // - char -> value is found successfully.
        // - null ->  indicates the absence of pre-received result.
        // - CLOSED -> we are out of bounds and further `next()` calls are not allowed.
        private var nextOrClosed: Any? = null // Char || null || CLOSED

        /**
         * Stores the leaf retrieved by [hasNext] call.
         * If [hasNext] has not been invoked yet,
         * or [hasNext] has not retrieved successfully an element, throws [IllegalStateException].
         */
        val currentLeaf: RopeLeafNode
            get() {
                val leaf = curNode as? RopeLeafNode
                check(nextOrClosed != null) { "`hasNext()` has not been invoked" }
                check(leaf != null) { "`hasNext()` has not retrieved a leaf" }
                return leaf
            }

        // internal API
        override fun findParent(child: RopeNode): RopeInternalNode? = links[child]

        // `hasNext()` is a special get() operation.
        open operator fun hasNext(): Boolean {
            if (nextOrClosed === CLOSED) return false
            return getImpl(
                index = nextIndex,
                root = curNode,
                stack = parentNodesRef,
                onOutOfBounds = { onOutOfBoundsHasNext() },
                onElementRetrieved = { leaf, i, element ->
                    onNextStack.push(leaf)
                    curIndex = i
                    curNode = leaf
                    nextOrClosed = element
                    nextIndex = curIndex + 1
                    true
                },
                //TODO: add comments
                onNextChild = {
                    onNextStack.push(it)
                    findParentInStackAndLink(it)
                }
            )
        }

        private fun onOutOfBoundsHasNext(): Boolean {
            nextOrClosed = CLOSED
            return false
        }

        open operator fun next(): Char {
            // Read the already received result or `null` if [hasNext] has not been invoked yet.
            val result = nextOrClosed
            check(result != null) { "`hasNext()` has not been invoked" }
            nextOrClosed = null
            // Is this iterator closed?
            if (nextOrClosed === CLOSED) throw NoSuchElementException(DEFAULT_CLOSED_MESSAGE)
            return result as Char
        }

        /**
         * Marks the iterator as closed and forbids any other subsequent [next] calls.
         */
        protected fun markClosed() {
            nextOrClosed = CLOSED
        }

        private fun findParentInStackAndLink(child: RopeNode) {
            if (child === root) return
            onNextStack.onEach {
                if (it === child) return@onEach
                val parent = it as? RopeInternalNode ?: return@onEach
                if (!parent.children.contains(child)) return@onEach
                links[child] = parent // link
            }
        }
    }

    inner class SingleElementRopeIterator(root: RopeNode, index: Int) : RopeIterator(root, index) {
        private var invoked = false

        override fun hasNext(): Boolean {
            if (invoked) {
                markClosed()
                return false
            }
            invoked = true
            return super.hasNext()
        }
    }

    private fun checkPositionIndex(index: Int) {
        if (index < 0) throw IndexOutOfBoundsException("index:$index")
    }

    private fun checkRangeIndexes(startIndex: Int, endIndex: Int) {
        if (startIndex < 0) throw IndexOutOfBoundsException("startIndex:$startIndex")
        if (endIndex < startIndex) {
            throw IndexOutOfBoundsException("End index ($endIndex) is less than start index ($startIndex).")
        }
    }

    // ###################
    // # Debug Functions #
    // ###################

    override fun toString(): String = root.toString()

    internal fun toStringDebug(): String = root.toStringDebug()
}

internal object EmptyRope : Rope(emptyRopeNode()) {
    override fun toString(): String = "Rope()"
    override fun equals(other: Any?): Boolean = other is Rope && other.isEmpty()

    override val length: Int = 0
    override fun collectLeaves(): List<RopeLeaf> = emptyList()
    override fun deleteAt(index: Int): Rope = throw IndexOutOfBoundsException("Rope is empty")
    override fun indexOf(element: Char): Int = -1
    override fun get(index: Int): Char? = null
    override fun insert(index: Int, element: String): Rope {
        if (index != 0) throw IndexOutOfBoundsException("index:$index, length:$length")
        val ropeNode = ropeNodeOf(element)
        return Rope(ropeNode)
    }

    override fun removeRange(startIndex: Int, endIndex: Int): Rope {
        throw IndexOutOfBoundsException("Rope is empty")
    }

    override fun plus(other: Rope): Rope {
        if (other.isEmpty()) return this
        return other
    }

    override fun subRope(startIndex: Int, endIndex: Int): Rope {
        if (startIndex == 0 && endIndex == 0) return this
        throw IndexOutOfBoundsException("startIndex: $startIndex, endIndex:$endIndex")
    }
}


fun Rope.insert(index: Int, element: Char): Rope = insert(index, element.toString())
fun Rope.isEmpty(): Boolean = length == 0

private fun RopeInternalNode.childrenIterator(): RopeInternalNodeChildrenIterator {
    return RopeInternalNodeChildrenIterator(weight, height, children)
}

private inline fun RopeInternalNodeChildrenIterator.nextChildOrElse(action: () -> RopeNode): RopeNode {
    return nextChildOrNull ?: action()
}

private inline fun RopeInternalNodeChildrenIterator.nextChildAndKeepRefOrElse(
    stack: ArrayStack<RopeInternalNodeChildrenIterator>,
    action: () -> RopeNode
): RopeNode = nextChildOrNull.let {
    if (it == null) {
        action()
    } else {
        stack.push(this)
        return it
    }
}

/**
 * A helper class to iterate through an internal node's children.
 */
internal class RopeInternalNodeChildrenIterator(
    weight: Int,
    height: Int,
    children: List<RopeNode>,
) : RopeInternalNode(weight, height, children) {
    var index = 0
        private set

    val nextChildOrNull: RopeNode? get() = if (hasNext()) next() else null

    operator fun next(): RopeNode {
        if (index >= children.size) throw NoSuchElementException()
        return children[index++]
    }

    operator fun hasNext(): Boolean {
        return index < children.size
    }

    fun tryIncIndex(): Boolean {
        if (index == children.lastIndex) return false
        index++
        return true
    }
}


// Internal result for [SingleIndexRopeIteratorWithHistory.nextOrClosed]
private val CLOSED = keb.Symbol("CLOSED")

private const val DEFAULT_CLOSED_MESSAGE = "iterator was closed"