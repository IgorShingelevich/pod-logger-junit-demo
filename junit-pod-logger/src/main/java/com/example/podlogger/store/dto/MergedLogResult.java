package com.example.podlogger.store.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.example.podlogger.client.PodLogDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MergedLogResult {

    private UUID testRunId;
    @Builder.Default
    private List<PodLogDto> fromPersistent = new ArrayList<>();
    @Builder.Default
    private List<PodLogDto> fromRuntime = new ArrayList<>();
    @Builder.Default
    private List<PodLogDto> merged = new ArrayList<>();
    private int insertedNewCount;
}
