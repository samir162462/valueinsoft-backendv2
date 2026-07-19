package com.example.valueinsoftbackend.Model.Configuration.AccessMap;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Hierarchy edge between two access-map nodes.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccessMapEdge {
    private String id;
    private String source;
    private String target;
    /** Currently always "hierarchy". */
    private String kind;
}
