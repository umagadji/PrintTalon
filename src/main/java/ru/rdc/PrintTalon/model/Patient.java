package ru.rdc.PrintTalon.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
public class Patient {
    private Long patientId;    // Идентификатор пациента в МИС
    private String fam;
    private String name;
    private String snils;
    private String birthdate;
    private String num;
}