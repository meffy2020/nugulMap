"use client"

import { FormEvent, useState } from "react"

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL || "https://api.nugulmap.com"

type SubmitState =
  | { kind: "idle" }
  | { kind: "submitting" }
  | { kind: "success"; receipt: string }
  | { kind: "error"; message: string }

const categories = [
  ["ACCOUNT_SUPPORT", "계정·로그인"],
  ["ACCOUNT_DELETION", "계정 삭제"],
  ["CONTENT_REPORT", "콘텐츠·사용자 신고"],
  ["PRIVACY", "개인정보"],
  ["OTHER", "기타 문의"],
] as const

type SupportCategory = (typeof categories)[number][0]

type SupportFormProps = {
  defaultCategory?: SupportCategory
  description?: string
  fixedCategory?: SupportCategory
  heading?: string
  submitLabel?: string
}

export function SupportForm({
  defaultCategory = "ACCOUNT_SUPPORT",
  description = "회신받을 이메일과 내용을 남겨 주세요. 앱 버전, 휴대전화 종류, 발생 시간을 함께 적으면 확인이 빨라집니다.",
  fixedCategory,
  heading = "문의 접수",
  submitLabel = "문의 접수하기",
}: SupportFormProps) {
  const [state, setState] = useState<SubmitState>({ kind: "idle" })
  const fixedCategoryLabel = fixedCategory
    ? categories.find(([value]) => value === fixedCategory)?.[1]
    : null

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = event.currentTarget
    const values = new FormData(form)

    setState({ kind: "submitting" })
    try {
      const response = await fetch(`${API_BASE_URL}/api/public/support/requests`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          category: fixedCategory ?? values.get("category"),
          email: values.get("email"),
          message: values.get("message"),
          website: values.get("website"),
        }),
      })
      const body = await response.json().catch(() => null)
      if (!response.ok) {
        const message = typeof body?.message === "string"
          ? body.message
          : "접수하지 못했습니다. 잠시 후 다시 시도해 주세요."
        throw new Error(message)
      }

      const receipt = String(body?.data?.request?.id ?? "완료")
      form.reset()
      setState({ kind: "success", receipt })
    } catch (error) {
      setState({
        kind: "error",
        message: error instanceof Error
          ? error.message
          : "접수하지 못했습니다. 잠시 후 다시 시도해 주세요.",
      })
    }
  }

  return (
    <form aria-labelledby="support-form-heading" className="space-y-5 rounded-lg border bg-card p-5" onSubmit={submit}>
      <div className="space-y-2">
        <h2 className="text-xl font-bold" id="support-form-heading">{heading}</h2>
        <p className="text-sm leading-7 text-muted-foreground">
          {description}
        </p>
      </div>

      {fixedCategory ? (
        <div className="grid gap-1 rounded-md border bg-muted/30 px-3 py-2 text-sm sm:grid-cols-[7rem_1fr]">
          <span className="font-semibold">문의 유형</span>
          <span>{fixedCategoryLabel}</span>
        </div>
      ) : (
        <label className="block space-y-2 text-sm font-semibold">
          문의 유형
          <select
            className="min-h-11 w-full rounded-md border bg-background px-3 py-2 font-normal"
            defaultValue={defaultCategory}
            name="category"
            required
          >
            {categories.map(([value, label]) => (
              <option key={value} value={value}>{label}</option>
            ))}
          </select>
        </label>
      )}

      <label className="block space-y-2 text-sm font-semibold">
        회신 이메일
        <input
          autoComplete="email"
          className="min-h-11 w-full rounded-md border bg-background px-3 py-2 font-normal"
          maxLength={255}
          name="email"
          placeholder="name@example.com"
          required
          type="email"
        />
      </label>

      <label className="block space-y-2 text-sm font-semibold">
        문의 내용
        <textarea
          className="min-h-40 w-full resize-y rounded-md border bg-background px-3 py-2 font-normal"
          maxLength={1000}
          name="message"
          placeholder="문제가 발생한 화면과 상황을 적어 주세요."
          required
        />
      </label>

      <label className="hidden" aria-hidden="true">
        웹사이트
        <input autoComplete="off" name="website" tabIndex={-1} />
      </label>

      <button
        className="inline-flex min-h-11 items-center rounded-md bg-foreground px-4 py-2 text-sm font-semibold text-background disabled:cursor-wait disabled:opacity-60"
        disabled={state.kind === "submitting"}
        type="submit"
      >
        {state.kind === "submitting" ? "접수 중…" : submitLabel}
      </button>

      <p className="text-xs leading-6 text-muted-foreground">
        이메일과 문의 내용은 요청 확인과 회신에 사용합니다. 자세한 보관·삭제 기준은{" "}
        <a className="font-semibold text-foreground underline" href="/privacy">개인정보 처리방침</a>에서 확인할 수 있습니다.
      </p>

      {state.kind === "success" && (
        <p className="rounded-md border border-emerald-500/40 bg-emerald-500/10 p-3 text-sm" role="status">
          접수되었습니다. 접수번호는 <strong>{state.receipt}</strong>입니다.
        </p>
      )}
      {state.kind === "error" && (
        <p className="rounded-md border border-red-500/40 bg-red-500/10 p-3 text-sm" role="alert">
          {state.message}
        </p>
      )}
    </form>
  )
}
