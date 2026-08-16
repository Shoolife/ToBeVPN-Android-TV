package com.tobevpn.tv.vpn

/**
 * Collapses concurrent Ethernet/Wi-Fi callbacks into one stable physical
 * upstream. Validated networks are preferred, while an unvalidated physical
 * network remains usable because Android's connectivity check is not a VPN
 * liveness verdict.
 */
internal class PhysicalNetworkSelector<T> {
    private val candidates = LinkedHashMap<T, Candidate>()
    private var selected: T? = null

    @Synchronized
    fun update(network: T, validated: Boolean, priority: Int): SelectionChange<T> {
        candidates[network] = Candidate(validated = validated, priority = priority)
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
            .maxByOrNull { it.value.selectionScore }
        val next = if (previous != null &&
            previousCandidate != null &&
            (best == null || previousCandidate.selectionScore >= best.value.selectionScore)
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

    private data class Candidate(
        val validated: Boolean,
        val priority: Int,
    ) {
        val selectionScore: Int
            get() = priority + if (validated) VALIDATED_PRIORITY_BONUS else 0
    }

    data class SelectionChange<T>(
        val type: ChangeType,
        val previous: T?,
        val current: T?,
    )

    enum class ChangeType { UNCHANGED, INITIAL, HANDOVER, UNAVAILABLE }

    private companion object {
        const val VALIDATED_PRIORITY_BONUS = 10_000
    }
}
