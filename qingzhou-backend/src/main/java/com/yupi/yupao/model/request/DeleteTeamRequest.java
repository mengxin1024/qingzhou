package com.yupi.yupao.model.request;

import lombok.Data;

import java.io.Serializable;

@Data
public class DeleteTeamRequest implements Serializable {

    private static final long serialVersionUID = -4162304142710323660L;

    /**
     * 队伍id
     */
    private Long teamId;
}
