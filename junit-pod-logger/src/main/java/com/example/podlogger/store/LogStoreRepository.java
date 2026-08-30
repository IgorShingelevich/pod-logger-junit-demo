package com.example.podlogger.store;

import java.util.List;

import com.example.podlogger.client.PodLogDto;
import com.example.podlogger.store.dto.LogQuery;

public interface LogStoreRepository {

    void saveAll(List<PodLogDto> logs);

    List<PodLogDto> find(LogQuery query);
}
