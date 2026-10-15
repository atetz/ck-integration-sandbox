import org.apache.camel.Exchange

import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Maps one TmsShipment (as a Map) to one CreateBrokerOrderMessage (as a Map).
 */
class ShipmentToBrokerOrderMapper {

    static final Map<String, String> PACKAGE_TYPES = [
        BALE    : 'BL',
        BOX     : 'BX',
        COIL    : 'CL',
        CRATE   : 'CR',
        CYLINDER: 'CY',
        DRUM    : 'DR',
        OTHER   : 'OT',
        PLT     : 'PL'
    ].asImmutable()

    // Always emits milliseconds, unlike Instant.toString() when millis are zero
    static final DateTimeFormatter MESSAGE_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

    Map map(Map shipment, Exchange exchange) {
        def context = exchange.context
        String senderId = context.resolvePropertyPlaceholders('{{broker.sender.id}}')
        String messageFunction = context.resolvePropertyPlaceholders('{{broker.message.function.new}}')

        List lineItems = (shipment.line_items ?: []) as List
        Map pickUpStop = findStop(shipment, 'PICKUP')
        Map deliveryStop = findStop(shipment, 'DELIVERY')

        [
            meta    : [
                senderId        : senderId,
                messageDate     : MESSAGE_DATE.format(Instant.now()),
                messageReference: shipment.id,
                messageFunction : messageFunction as Integer
            ],
            shipment: [
                reference    : shipment.id,
                carrier      : shipment.customer?.carrier,
                transportMode: 'ROAD',
                orders       : [[
                    reference       : shipment.id,
                    goodsDescription: lineItems.collect { it.description }.join('|'),
                    quantity        : [
                        grossWeight  : totalGrossWeight(lineItems),
                        loadingMeters: shipment.loading_meters
                    ],
                    pickUp          : mapLocation(pickUpStop),
                    consignee       : mapLocation(deliveryStop),
                    handlingUnits   : handlingUnits(lineItems)
                ]]
            ]
        ]
    }

    private static Map findStop(Map shipment, String type) {
        Map stop = (shipment.stops ?: []).find { it.type == type } as Map
        if (stop == null) {
            throw new IllegalArgumentException("Shipment ${shipment.id} has no ${type} stop")
        }
        stop
    }

    private static Map mapLocation(Map stop) {
        Map location = stop.location as Map ?: [:]
        Map address = location.address as Map ?: [:]
        [
            identification: location.code,
            name          : location.name,
            address1      : address.address,
            address2      : null,
            city          : address.city,
            postalCode    : address.postal_code,
            country       : address.country,
            latitude      : location.latitude,
            longitude     : location.longitude,
            instructions  : null,
            dates         : [
                [qualifier: 'PERIOD_EARLIEST', dateTime: "${stop.planned_date}T${stop.planned_time_window_start}Z".toString()],
                [qualifier: 'PERIOD_LATEST', dateTime: "${stop.planned_date}T${stop.planned_time_window_end}Z".toString()]
            ]
        ]
    }

    private static BigDecimal totalGrossWeight(List lineItems) {
        lineItems.sum(BigDecimal.ZERO) { item ->
            (item.package_weight as BigDecimal) * (item.total_packages as BigDecimal)
        }.setScale(2, RoundingMode.HALF_UP)
    }

    private static List<Map> handlingUnits(List lineItems) {
        lineItems.collectMany { item ->
            String packageType = PACKAGE_TYPES[item.package_type as String]
            if (packageType == null) {
                throw new IllegalArgumentException("Unmapped package_type '${sanitize(item.package_type)}'")
            }
            int packages = item.total_packages as int
            if (packages < 1) {
                throw new IllegalArgumentException("Line item total_packages must be at least 1, was ${packages}")
            }
            (0..<packages).collect {
                [
                    packagingQualifier: packageType,
                    grossWeight       : item.package_weight,
                    width             : item.width,
                    length            : item.length,
                    height            : item.height
                ]
            }
        }
    }

    // Keeps TMS-supplied values from forging log lines when echoed in an exception message
    private static String sanitize(Object value) {
        String.valueOf(value).replaceAll(/[^A-Za-z0-9_-]/, '?').take(40)
    }
}
