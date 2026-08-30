package com.example.podlogger.parser;

import java.util.List;

import com.example.podlogger.client.PodLogDto;

public interface LogParser {

    List<PodLogDto> parse(String rawDump);
}
