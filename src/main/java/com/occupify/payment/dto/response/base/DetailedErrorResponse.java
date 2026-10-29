package com.occupify.payment.dto.response.base;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@JsonPropertyOrder({"httpStatus", "message", "errorCode", "timestamp", "path", "items"})
public class DetailedErrorResponse extends ErrorResponse {
    private Map<String, List<String>> items;
}
