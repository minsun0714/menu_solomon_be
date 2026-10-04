package com.menusolomon.vote.dto;

import com.menusolomon.common.exception.BusinessException;
import com.menusolomon.common.exception.ErrorCode;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record VoteUpdateRequest(@Size(max = 40) @Pattern(regexp = "(?s).*\\S.*") String name, Instant closesAt) {
    @com.fasterxml.jackson.annotation.JsonCreator(mode = com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING)
    public static VoteUpdateRequest parse(tools.jackson.databind.JsonNode body) {
        if (!body.isObject() || body.size() == 0 || body.has("name") && !body.get("name").isTextual()
                || body.has("closesAt") && !body.get("closesAt").isTextual())
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        try {
            return new VoteUpdateRequest(body.has("name") ? body.get("name").textValue() : null,
                    body.has("closesAt") ? Instant.parse(body.get("closesAt").textValue()) : null);
        } catch (java.time.format.DateTimeParseException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }
}
