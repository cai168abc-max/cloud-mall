#!/bin/bash

# RocketMQ Topic 创建脚本
# 使用方法: ./create-topics.sh

NAMESRV_ADDR=${1:-localhost:9876}

echo "开始创建MQ Topic..."
echo "NameServer: $NAMESRV_ADDR"

# 创建验证码Topic
echo "创建 verify-code-topic..."
sh mqadmin updateTopic -n $NAMESRV_ADDR -t verify-code-topic -c DefaultCluster -o 3 -s 8

# 创建订单通知Topic
echo "创建 order-notify-topic..."
sh mqadmin updateTopic -n $NAMESRV_ADDR -t order-notify-topic -c DefaultCluster -o 3 -s 8

# 创建秒杀Topic
echo "创建 seckill-topic..."
sh mqadmin updateTopic -n $NAMESRV_ADDR -t seckill-topic -c DefaultCluster -o 5 -s 16

echo "Topic创建完成！"
echo ""
echo "查看所有Topic:"
sh mqadmin topicList -n $NAMESRV_ADDR
