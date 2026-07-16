package com.raredonut.mnarchive.imports;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.json.JsonMapper;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * 북마클릿이 e-amusement 페이지에서 호출하는 유일한 엔드포인트.
 *
 * Content-Type 이 text/plain 인 것은 의도적이다 — CORS 'simple request' 조건을 만족시켜
 * 프리플라이트(OPTIONS)를 없앤다. 그래서 Spring 의 자동 JSON 바인딩을 못 쓰고 직접 파싱한다.
 * 토큰도 같은 이유로 Authorization 헤더가 아니라 바디에 담긴다.
 */
@RestController
@RequestMapping("/api/imports")
public class ImportController {

    private static final int MAX_RECORDS = 10_000;

    private final JsonMapper jsonMapper;
    private final Validator validator;
    private final ImportService importService;

    public ImportController(JsonMapper jsonMapper, Validator validator, ImportService importService) {
        this.jsonMapper = jsonMapper;
        this.validator = validator;
        this.importService = importService;
    }

    @PostMapping(consumes = MediaType.TEXT_PLAIN_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ImportResult create(@RequestBody String rawBody) {
        ImportRequest req;
        try {
            // Jackson 3 는 unchecked 예외를 던진다(JacksonException). checked 가 아니다.
            req = jsonMapper.readValue(rawBody, ImportRequest.class);
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "malformed body");
        }

        if (req.token() == null || req.token().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        if (req.records() == null || req.records().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "no records");
        }
        if (req.records().size() > MAX_RECORDS) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
        }
        for (ScoreRow row : req.records()) {
            if (!validator.validate(row).isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid record");
            }
        }

        return importService.ingest(req.token(), req.source(), req.records());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ImportRequest(String token, String source, List<@Valid ScoreRow> records) {}
}