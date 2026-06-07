package app.sift.domain.agent

import app.sift.domain.llm.ToolSpec

/**
 * Agent 可调用的本地工具。
 *
 * v0.1 的单次调用模式暂不走工具循环，但接口先定好：v0.2 升级为多轮 tool-use 循环时
 * （decide_keep / search_similar / tag_and_categorize / relate / save_note）直接复用本接口，
 * 不必重写 [CaptureAgent] 的对外签名。
 */
interface Tool {
    val spec: ToolSpec

    /** 执行工具。入参为模型给出的 JSON 字符串，返回喂回模型的结果文本。 */
    suspend fun execute(argumentsJson: String): String
}
