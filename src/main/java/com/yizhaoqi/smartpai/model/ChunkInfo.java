package com.yizhaoqi.smartpai.model;

import jakarta.persistence.*;
import lombok.Data;

/**
 * ChunkInfo 类用于表示文件分块的信息
 * 它是一个实体类，与数据库中的 'chunk_info' 表对应
 * 该类用来存储每个文件分块的元数据，包括分块的唯一标识、属于哪个文件、分块的顺序、分块的校验码和存储位置
 */
@Data
@Entity
@Table(name = "chunk_info")
public class ChunkInfo {
    /**
     * 分块信息的唯一标识符
     * 由数据库自动生成，用于唯一确定一个分块信息
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 文件的MD5值
     * 用于标识一个文件，同一个文件的MD5值相同，不同文件的MD5值不同
     */
    private String fileMd5;

    /**
     * 分块的索引号
     * 表示文件中的第几个分块，用于保持分块的顺序
     */
    private int chunkIndex;

    /**
     * 分块的MD5值
     * 每个分块的唯一标识，用于校验分块的完整性和正确性
     */
    private String chunkMd5;

    /**
     * 分块的存储路径
     * 表示分块在系统中的存储位置，可以是绝对路径或相对路径
     */
    private String storagePath;
}

