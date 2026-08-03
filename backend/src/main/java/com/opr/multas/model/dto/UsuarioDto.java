package com.opr.multas.model.dto;

import com.opr.multas.model.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioDto {

    private Long id;
    private String nome;

    public static UsuarioDto from(Usuario u) {
        if (u == null) {
            return null;
        }
        return new UsuarioDto(u.getId(), u.getNome());
    }
}