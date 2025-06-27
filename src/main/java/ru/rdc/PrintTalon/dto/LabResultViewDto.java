package ru.rdc.PrintTalon.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LabResultViewDto {
    private String ids;
    private String material;
    private String otd;
    private String usl;
    private String collecdate;
    private String finisdate;
}