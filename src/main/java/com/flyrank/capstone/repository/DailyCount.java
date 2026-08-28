package com.flyrank.capstone.repository;
import java.time.LocalDate;
public interface DailyCount {
    LocalDate getDay();
    Long getCount();
}