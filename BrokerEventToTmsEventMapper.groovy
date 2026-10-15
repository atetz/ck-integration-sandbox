/**
 * Maps one BrokerEventMessage (as a Map) to one CreateTmsShipmentEvent (as a Map).
 * order.eta is deliberately not mapped: CreateTmsShipmentEvent has no field for it.
 */
class BrokerEventToTmsEventMapper {

    static final Map<String, String> EVENT_TYPES = [
        ORDER_CREATED  : 'BOOKED',
        CANCEL_ORDER   : 'CANCELLED',
        DRIVING_TO_LOAD: 'DISPATCHED',
        ORDER_LOADED   : 'PICKED_UP',
        ETA_EVENT      : 'ETA_CHANGED',
        ORDER_DELIVERED: 'DELIVERED'
    ].asImmutable()

    // shipmentId is interpolated into a dynamic endpoint URI, so it must be a strict UUID.
    static final String UUID_PATTERN = /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/

    String shipmentId(Map event) {
        String id = event?.shipmentId as String
        if (id == null || !(id ==~ UUID_PATTERN)) {
            throw new IllegalArgumentException('Broker event shipmentId is not a valid UUID')
        }
        id
    }

    Map map(Map event) {
        Map situation = event.situation as Map ?: [:]
        String eventType = EVENT_TYPES[situation.event as String]
        if (eventType == null) {
            throw new IllegalArgumentException("Unmapped broker event type '${String.valueOf(situation.event).replaceAll(/[^A-Za-z0-9_-]/, '?').take(40)}'")
        }

        Map result = [
            created_at              : situation.registrationDate,
            occured_at              : situation.actualDate,
            event_type              : eventType,
            external_order_reference: event.order?.reference,
            source                  : 'broker'
        ]
        Map position = situation.position as Map
        if (position != null) {
            result.location = [
                code     : position.locationReference,
                latitude : position.latitude,
                longitude: position.longitude
            ]
        }
        result
    }
}
