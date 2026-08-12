package com.tobevpn.tv.vpn

/** Collapses concurrent Ethernet/Wi-Fi callbacks into one stable physical upstream. */
internal class PhysicalNetworkSelector<T> {
    private val candidates = LinkedHashMap<T, Candidate>()
    private var selected: T? = null

    @Synchronized
    fun update(network: T, validated: Boolean, priority: Int): SelectionChange<T> {
        candidates[network] = Candidate(validated, priority)
        return reselect()
    }

    @Synchronized
    fun onLost(network: T): SelectionChange<T> {
        candidates.remove(network)
        return reselect()
    }

    @Synchronized
    fun selectedOrNull(): T? = selected

    @Synchronized
    fun isSelected(network: T): Boolean = selected == network

    @Synchronized
    fun hasUsableNetwork(): Boolean = selected != null

    @Synchronized
    fun reset() {
        candidates.clear()
        selected = null
    }

    private fun reselect(): SelectionChange<T> {
        val previous = selected
        val previousCandidate = previous?.let(candidates::get)
        val best = candidates.entries
            .asSequence()
            .filter { it.value.validated }
            .maxByOrNull { it.value.priority }
        val next = if (previous != null &&
            previousCandidate?.validated == true &&
            (best == null || previousCandidate.priority >= best.value.priority)
        ) {
            previous
        } else {
            best?.key
        }
        selected = next
        val type = when {
            previous == next -> ChangeType.UNCHANGED
            previous == null && next != null -> ChangeType.INITIAL
            previous != null && next == null -> ChangeType.UNAVAILABLE
            else -> ChangeType.HANDOVER
        }
        return SelectionChange(type, previous, next)
    }

    private data class Candidate(val validated: Boolean, val priority: Int)

    data class SelectionChange<T>(
        val type: ChangeType,
        val previous: T?,
        val current: T?,
    )

    enum class ChangeType { UNCHANGED, INITIAL, HANDOVER, UNAVAILABLE }
}
