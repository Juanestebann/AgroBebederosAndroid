package cl.ariztia.bebederos.viewmodel

import cl.ariztia.bebederos.data.model.MAX_WATER_TEMPERATURE_C
import cl.ariztia.bebederos.data.model.MIN_WATER_TEMPERATURE_C
import java.math.BigDecimal
import java.math.RoundingMode

/** Resultado analizado de un campo térmico, con valor o mensaje de error excluyentes. */
internal data class WaterTemperatureInput(
    val value: Double? = null,
    val decimalValue: BigDecimal? = null,
    val error: String? = null
) {
    val isValid: Boolean
        get() = value != null && decimalValue != null && error == null
}

/**
 * Acepta coma o punto decimal y exige un número finito dentro del rango compartido 0–50 °C.
 */
internal fun validateWaterTemperatureInput(
    input: String,
    fieldName: String
): WaterTemperatureInput {
    val normalized = input.trim().replace(',', '.')
    if (normalized.isBlank()) {
        return WaterTemperatureInput(error = "Ingresa la temperatura $fieldName del flushing.")
    }

    val decimal = normalized.toBigDecimalOrNull()
    if (decimal == null) {
        return WaterTemperatureInput(
            error = "Ingresa una temperatura $fieldName válida usando coma o punto decimal."
        )
    }

    val minimum = BigDecimal.valueOf(MIN_WATER_TEMPERATURE_C)
    val maximum = BigDecimal.valueOf(MAX_WATER_TEMPERATURE_C)
    if (decimal < minimum || decimal > maximum) {
        return WaterTemperatureInput(
            error = "La temperatura $fieldName debe estar entre " +
                "${MIN_WATER_TEMPERATURE_C.toInt()} y ${MAX_WATER_TEMPERATURE_C.toInt()} °C."
        )
    }

    val normalizedDecimal = try {
        decimal.setScale(2, RoundingMode.UNNECESSARY)
    } catch (_: ArithmeticException) {
        return WaterTemperatureInput(
            error = "La temperatura $fieldName puede tener como máximo dos decimales."
        )
    }

    return WaterTemperatureInput(
        value = normalizedDecimal.toDouble(),
        decimalValue = normalizedDecimal
    )
}
