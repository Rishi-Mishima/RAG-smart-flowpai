#!/bin/bash
# MinIO 文件迁移脚本：将旧文件（文件名路径）迁移到新路径（MD5路径）

# 配置
MYSQL_HOST="localhost"
MYSQL_USER="root"
MYSQL_PASS="123456"
MYSQL_DB="PaiSmart"
MINIO_ALIAS="myminio"
MINIO_BUCKET="uploads"

echo "=== MinIO 文件迁移脚本 ==="
echo "开始时间: $(date)"

# 1. 从MySQL获取所有文件记录（包含MD5和文件名）
echo ""
echo "步骤 1: 从MySQL获取文件记录..."
mysql -h$MYSQL_HOST -u$MYSQL_USER -p$MYSQL_PASS $MYSQL_DB -N -e "
SELECT file_md5, file_name, user_id
FROM file_upload
WHERE status = 'COMPLETED'
ORDER BY created_at;" > /tmp/files_list.txt

echo "找到 $(wc -l < /tmp/files_list.txt) 个文件记录"

# 2. 遍历每个文件，重命名MinIO中的对象
echo ""
echo "步骤 2: 重命名MinIO对象..."
SUCCESS_COUNT=0
SKIP_COUNT=0
ERROR_COUNT=0

while IFS=$'\t' read -r file_md5 file_name user_id; do
    OLD_PATH="merged/$file_name"
    NEW_PATH="merged/$file_md5"

    echo "处理: $file_name (MD5: $file_md5)"

    # 检查旧路径是否存在
    if mc stat $MINIO_ALIAS/$MINIO_BUCKET/$OLD_PATH >/dev/null 2>&1; then

        # 检查新路径是否已存在
        if mc stat $MINIO_ALIAS/$MINIO_BUCKET/$NEW_PATH >/dev/null 2>&1; then
            echo "  ⚠️  新路径已存在，删除旧路径"
            mc rm $MINIO_ALIAS/$MINIO_BUCKET/$OLD_PATH
            SKIP_COUNT=$((SKIP_COUNT + 1))
        else
            # 复制到新路径
            if mc cp $MINIO_ALIAS/$MINIO_BUCKET/$OLD_PATH $MINIO_ALIAS/$MINIO_BUCKET/$NEW_PATH; then
                # 删除旧路径
                mc rm $MINIO_ALIAS/$MINIO_BUCKET/$OLD_PATH
                echo "  ✅ 迁移成功: $OLD_PATH -> $NEW_PATH"
                SUCCESS_COUNT=$((SUCCESS_COUNT + 1))
            else
                echo "  ❌ 迁移失败: $OLD_PATH"
                ERROR_COUNT=$((ERROR_COUNT + 1))
            fi
        fi
    else
        echo "  ⚠️  旧路径不存在，可能已迁移"
        SKIP_COUNT=$((SKIP_COUNT + 1))
    fi

done < /tmp/files_list.txt

# 3. 验证迁移结果
echo ""
echo "步骤 3: 验证迁移结果..."
echo "MinIO merged 目录内容:"
mc ls $MINIO_ALIAS/$MINIO_BUCKET/merged/

# 清理临时文件
rm -f /tmp/files_list.txt

# 总结
echo ""
echo "=== 迁移完成 ==="
echo "成功迁移: $SUCCESS_COUNT 个文件"
echo "跳过/已存在: $SKIP_COUNT 个文件"
echo "失败: $ERROR_COUNT 个文件"
echo "结束时间: $(date)"
