import os
import openai

# 環境変数からAPIキーを取得します
api_key = os.getenv("OPENAI_API_KEY")

# APIキーが正しく取得できているか確認します
if not api_key:
    raise ValueError("APIキーが設定されていません。APIキーを確認してください。")
else:
    print(f"APIキーが正しく設定されています: {api_key}")

# OpenAIクライアントの設定を行います
openai.api_key = api_key

# レスポンスを取得する関数です
def get_response(prompt, stream=False):
    try:
        # レスポンスを取得します
        response = openai.ChatCompletion.create(
            model="gpt-4o-mini",
            messages=[{"role": "user", "content": prompt}],
            stream=stream,
        )
        # ストリームレスポンスの処理を行います
        if stream:
            for chunk in response:
                if (
                    isinstance(chunk, dict)
                    and "choices" in chunk
                    and chunk["choices"][0]["delta"].get("content")
                ):
                    print(chunk["choices"][0]["delta"]["content"], end="")
        else:
            print(response["choices"][0]["message"]["content"])
    except openai.error.OpenAIError as e:
        # エラーが発生した場合の処理です
        print(f"レスポンス中のエラー: {e}")

# メインの処理を行います
if __name__ == "__main__":
    while True:
        # ユーザーからの入力を受け取ります
        prompt = input("あなた: ")
        if prompt.lower() in ["exit", "quit", "q"]:
            break
        # レスポンスを取得して表示します
        get_response(prompt, stream=True)
        print()  # 改行を追加