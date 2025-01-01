import openai

# APIキーを直接設定します
api_key = "ここにAPIキーを貼り付ける"

# APIキーが正しく取得できているか確認します
if not api_key:
    raise ValueError("APIキーが設定されていません。APIキーを確認してください。")
else:
    print(f"APIキーが正しく設定されています")

# OpenAIクライアントの設定を行います
openai.api_key = api_key

# ストリーミングレスポンスをテストする関数です
def test_streaming_response():
    try:
        # ストリーミングレスポンスを取得します
        response = openai.ChatCompletion.create(
            model="gpt-4o-mini",
            messages=[{"role": "user", "content": "ストリーミングテスト。こんにちは"}],
            stream=True,
        )
        # ストリームからのデータを処理します
        for chunk in response:
            if (
                isinstance(chunk, dict)
                and "choices" in chunk
                and chunk["choices"][0]["delta"].get("content")
            ):
                print(chunk["choices"][0]["delta"]["content"], end="")
    except openai.error.OpenAIError as e:
        # エラーが発生した場合の処理です
        print(f"ストリーミングレスポンス中のエラー: {e}")

# 非ストリーミングレスポンスをテストする関数です
def test_non_streaming_response():
    try:
        # 非ストリーミングレスポンスを取得します
        response = openai.ChatCompletion.create(
            model="gpt-4o-mini",
            messages=[{"role": "user", "content": "非ストリーミングテスト。こんばんは"}],
        )
        print(response["choices"][0]["message"]["content"])
    except openai.error.OpenAIError as e:
        # エラーが発生した場合の処理です
        print(f"非ストリーミングレスポンス中のエラー: {e}")

# テスト関数を呼び出します
if __name__ == "__main__":
    test_streaming_response()
    test_non_streaming_response()
    print("何かお手伝いできること～などが返れば、API接続成功です")
