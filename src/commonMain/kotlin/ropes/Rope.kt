package keb.ropes

import keb.classSimpleName
import keb.hexAddress
import kotlin.math.min

open class Rope(value: String)

// btree impl

open class NodeInfo //TODO: impl later, but it seems it is not needed for btree impl

/**
 * Represents a self-balancing tree node.
 */
// Note:
// Each leaf node contains a string (see note below) and its length, called weight. Each intermediate node also contains
// a weight which is the sum of all the leaves in its left subtree.
sealed class BTreeNode(
    /**
     * If this node is a leaf, then this value represents the length of the string. Else holds the sum of the lengths of
     * all the leaves in its left subtree.
     */
    val weight: Int,
    /**
     * The `height` is measured as the number of edges in the longest path from the root node to a leaf node. If this
     * node is the `root`, then it represents the `height` of the entire tree. By extension, if this is a leaf node
     * then, it should be `0`.
     */
    val height: Int, // will be used for re-balancing.
) : Iterable<LeafNode> {
    abstract val isInternalNode: Boolean
    abstract val isLeafNode: Boolean

    val isEmpty: Boolean = weight == 0
//    val isRoot: Boolean = height == 0

    fun insert(value: String) {
        // To insert a new element, search the tree to find the leaf node where the new element should be added
        read32Chunks(value) //TODO: this is only for bulk reading
    }

    open operator fun get(index: Int): LeafNode? {
        if (index < 0) return null
        return LazyPathFinder(this, index).getOrNull()
    }

    private class LazyPathFinder(var curNode: BTreeNode, val index: Int) {
        var curIndex = 0

        fun getOrNull(): LeafNode? {
            if (curIndex > index) return null
            when (curNode) {
                is InternalNode -> {
                    for (node in (curNode as InternalNode).children) {
                        curNode = node
                        return getOrNull() ?: continue
                    }
                }

                is LeafNode -> {
                    if (curIndex == index) return curNode as LeafNode
                    curIndex++
                }
            }
            return null
        }
    }

    fun find(value: String): LeafNode? {
        val len = value.length
        if (len > weight) return null
        return when (this) {
            is InternalNode -> children.find(value, len)
            is LeafNode -> {
                if (this.value.contains(value)) {
                    this
                } else {
                    null
                }
            }
        }
    }

    private fun List<BTreeNode>.find(value: String, len: Int): LeafNode? {
        for (node in this) {
            if (len > weight) continue
            when (node) {
                is InternalNode -> node.children.find(value, len)
                is LeafNode -> if (node.value.contains(value)) return node
            }
        }
        return null
    }

    override fun iterator(): Iterator<LeafNode> {
        return when (this) {
            is InternalNode -> BTreeNodeIterator(this)
            is LeafNode -> SingleBTreeNodeIterator(this)
        }
    }

    // ###################
    // # Debug Functions #
    // ###################

//    override fun toString(): String {
//        val sb = StringBuilder()
//        sb.append("$classSimpleName(")
//        sb.append("weight=$weight,")
//        if (isInternalNode) {
//            sb.append("children=$children,")
//            sb.append("isInternalNode=true")
//        } else {
//            sb.append("value=$value,")
//            sb.append("isLeafNode=true")
//        }
//        sb.append(")")
//        return sb.toString()
//    }

    internal fun toStringDebug(): String {
        val sb = StringBuilder()
        sb.append("$classSimpleName@$hexAddress(")
        sb.append("weight=$weight,")
        if (isInternalNode) {
            sb.append("isInternalNode=true")
        } else {
            sb.append("isLeafNode=true")
        }
        sb.append(")")
        return sb.toString()
    }
}

//fun bTreeOf(value: String): BTreeNode {
//    require(value.isNotBlank()) { "input string cannot be blank" }
//    require(value.isNotEmpty()) { "input string cannot be empty" }
//    return BTreeNode(value.length, value)
//}

// some questions
// 1. how we define how much of a string each node should hold?
// 2. should we ever edit a node? --> probably not, better to create a new one.

/**
 * Represents a leaf-node in a [B-tree](https://en.wikipedia.org/wiki/B-tree#Notes).
 */
class LeafNode(val value: String) : BTreeNode(value.length, 0) {
    override val isInternalNode: Boolean = false
    override val isLeafNode: Boolean = true

    val length: Int = value.length


    // Returns BTreeNode
    fun pushAndMaybeSplit(other: String, index: Int): BTreeNode {
//        if (stringFits(other)) { // no-splitting
//            return LeafNode(value + other, height)
//        } else { // splits
//
//        }
        TODO()
    }

//    override operator fun get(index: Int): Char = value[index]

    //TODO: Future improvement (prob) Try to split at newline boundary (leaning left), if not, then split at codepoint.
    private fun split(other: String) {
        if (true) { // isRoot
            // find leaf to split
            val splitPoint = min(MAX_SIZE_LEAF, length)
            val stringToBeSplit = value + other
            val leftString = stringToBeSplit.substring(0 until splitPoint)
            val rightString = stringToBeSplit.substring(splitPoint)
//            val leftNode = LeafNode()
//            val newRoot = InternalNode(leftString.length, listOf(LeafNode(leftString, newRoot)))
            //TODO: builder
//            buildList<> { }
        } else { // slow-path

        }
    }

    //TODO: better name?
    private fun stringFits(value: String): Boolean = length + value.length < MAX_SIZE_LEAF
}

private const val MIN_CHILDREN = 4
private const val MAX_CHILDREN = 8

private const val MAX_SIZE_LEAF = 2048

/**
 * Represents an internal-node in a [B-tree](https://en.wikipedia.org/wiki/B-tree#Notes).
 */
// Notes:
// Internal node does not have insert operation().
open class InternalNode(
    weight: Int,
    height: Int,
    val children: List<BTreeNode> = listOf(),
) : BTreeNode(weight, height) {
    override val isInternalNode: Boolean = true
    override val isLeafNode: Boolean = false
}

/**
 * Reads up to 32 chunks of characters and creates a node from them. The process is repeated until the end of [input].
 */
fun read32Chunks(input: String): List<BTreeNode> { //TODO: make it private?
    val chunks = readChunksOf64Chars(input)
    return buildList {
        chunks.chunked(CHUNK_NUMBER).forEach { chunks ->
            val valueNode = chunks.joinToString(separator = "") { it }
//            add(BTreeNode(valueNode.length, valueNode))
            TODO()
        }
    }
}

private fun readChunksOf64Chars(input: String): List<String> {
    val chunks = mutableListOf<String>()
    val len = input.length
    var chunkCount = 0
    val chunkBuilder = StringBuilder()
    for (i in 0 until len) {
        if (chunkCount == CHUNK_SIZE) {
            val chunk = chunkBuilder.toString()
            chunks.add(chunk)
            chunkBuilder.clear()
            chunkCount = 0
        }
        if (i == len - 2) {
            chunkBuilder.append(input[i])
            val chunk = chunkBuilder.toString()
            chunks.add(chunk)
            break
        }
        chunkBuilder.append(input[i])
        chunkCount++
    }
    return chunks
}

const val CHUNK_NUMBER = 32
const val CHUNK_SIZE = 64