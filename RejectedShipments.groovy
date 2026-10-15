import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory set of shipment ids the broker rejected with 422.
 *
 * The set is static so every bean instance Camel creates (filter predicate, onException handler)
 * shares it. It is deliberately not persisted: a restart re-logs each poison shipment once.
 */
class RejectedShipments {

    private static final Set<String> REJECTED = ConcurrentHashMap.newKeySet()

    boolean isNotRejected(Map shipment) {
        String id = shipment?.id as String
        id == null || !REJECTED.contains(id)
    }

    void markRejected(String shipmentId) {
        if (shipmentId) {
            REJECTED.add(shipmentId)
        }
    }
}
