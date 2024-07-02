package com.webhook.notion_jandi_webhook;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.util.Timer;
import java.util.TimerTask;

public class NotionJandiWebhookApplication {
	private static final String NOTION_API_KEY = "";
	private static final String DATABASE_ID = "";
	private static final String JANDI_WEBHOOK_URL = "";
	private static Instant lastCheckedTime = Instant.now();

	public static void main(String[] args) {
		Timer timer = new Timer();
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				try {
					checkNotionAndSendToJandi();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}, 0, 60000); // 1분마다 실행
	}

	private static void checkNotionAndSendToJandi() throws Exception {
		String notionResponse = getNotionDatabaseItems();
		JSONObject jsonObject = new JSONObject(notionResponse);
		JSONArray results = jsonObject.getJSONArray("results");

		for (int i = 0; i < results.length(); i++) {
			JSONObject item = results.getJSONObject(i);
			Instant createdTime = Instant.parse(item.getString("created_time"));
			if (createdTime.isAfter(lastCheckedTime) || createdTime.equals(lastCheckedTime)) {
				String title = item.getJSONObject("properties").getJSONObject("Title").getJSONArray("title").getJSONObject(0).getString("plain_text");
				String tag = item.getJSONObject("properties").getJSONObject("Tag").getJSONArray("multi_select").getJSONObject(0).getString("name");
				String content = item.getJSONObject("properties").getJSONObject("Content").getJSONArray("rich_text").getJSONObject(0).getString("plain_text");
				String message = "title = " + title + "\n";
				       message += "questioner = " + tag + "\n";
				       message += "Content = " + content;
				sendMessageToJandi(message);
			}
		}
		lastCheckedTime = Instant.now();
		// 초 단위를 00으로 설정 (나노초도 0으로 설정)
		Instant truncatedNow = lastCheckedTime.truncatedTo(ChronoUnit.MINUTES);
		// Instant 객체를 ISO 8601 형식 문자열로 포맷
		String formattedDateTime = DateTimeFormatter.ISO_INSTANT.format(truncatedNow);
		lastCheckedTime = Instant.parse(formattedDateTime);
		System.out.println(lastCheckedTime);
	}

	private static String getNotionDatabaseItems() throws Exception {
		CloseableHttpClient httpClient = HttpClients.createDefault();
		HttpPost request = new HttpPost("https://api.notion.com/v1/databases/" + DATABASE_ID + "/query");
		request.addHeader("Authorization", "Bearer " + NOTION_API_KEY);
		request.addHeader("Notion-Version", "2022-06-28");
		request.addHeader("Content-Type", "application/json");

		CloseableHttpResponse response = httpClient.execute(request);
		BufferedReader reader = new BufferedReader(new InputStreamReader(response.getEntity().getContent(), StandardCharsets.UTF_8));
		StringBuilder stringBuilder = new StringBuilder();
		String line;
		while ((line = reader.readLine()) != null) {
			stringBuilder.append(line);
		}

		httpClient.close();
		return stringBuilder.toString();
	}

	private static void sendMessageToJandi(String message) throws Exception {
		CloseableHttpClient httpClient = HttpClients.createDefault();
		HttpPost request = new HttpPost(JANDI_WEBHOOK_URL);
		request.addHeader("Content-Type", "application/json");

		JSONObject json = new JSONObject();
		json.put("body", message);

		StringEntity entity = new StringEntity(json.toString(), StandardCharsets.UTF_8);
		request.setEntity(entity);

		CloseableHttpResponse response = httpClient.execute(request);
		if (response.getCode() == 200) {
			System.out.println("Message sent to Jandi successfully");
		} else {
			System.out.println("Failed to send message to Jandi. Status code: " + response.getCode());
		}

		httpClient.close();
	}
}
