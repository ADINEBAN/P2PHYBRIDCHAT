package com.example.client.util;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.scene.Node;

import java.util.function.Consumer;

public class EmojiHandler {

    // Danh sách Emoji cơ bản (Bạn có thể thêm nhiều hơn)
    private static final String[] EMOJIS = {
            "😀", "😁", "😂", "🤣", "😃", "😄", "😅", "😆", "😉", "😊",
            "😋", "😎", "😍", "😘", "🥰", "😗", "😙", "😚", "🙂", "🤗",
            "🤩", "🤔", "🤨", "😐", "😑", "😶", "🙄", "😏", "😣", "😥",
            "😮", "🤐", "😯", "😪", "😫", "😴", "😌", "😛", "😜", "😝",
            "🤤", "😒", "😓", "😔", "😕", "🙃", "🤑", "😲", "☹️", "🙁",
            "😖", "😞", "😟", "😤", "😢", "😭", "f😦", "😧", "😨", "😩",
            "🤯", "😬", "😰", "😱", "🥵", "🥶", "😳", "🤪", "😵", "😡",
            "😠", "🤬", "😷", "🤒", "🤕", "🤢", "🤮", "🤧", "😇", "🥳",
            "🥺", "🤠", "🤡", "🤥", "🤫", "🤭", "🧐", "🤓", "😈", "👿",
            "👻", "💀", "☠️", "👽", "👾", "🤖", "💩", "👍", "👎", "👊",
            "👌", "✌️", "🤘", "🤟", "👈", "👉", "👆", "👇", "✋", "👋"
    };

    // Danh sách cảm xúc nhanh (Reaction)
    public static final String[] REACTIONS = {"👍", "❤️", "😆", "😮", "😢", "😠"};

    public static void showEmojiPopup(Node anchorNode, Consumer<String> onEmojiSelected) {
        Popup popup = new Popup();

        FlowPane flowPane = new FlowPane();
        flowPane.setHgap(5);
        flowPane.setVgap(5);
        flowPane.setPrefWidth(300);
        flowPane.setPadding(new Insets(10));
        flowPane.setStyle("-fx-background-color: white; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 10, 0, 0, 0); -fx-background-radius: 10;");

        for (String emoji : EMOJIS) {
            Button btn = new Button(emoji);
            btn.setStyle("-fx-background-color: transparent; -fx-font-size: 18px; -fx-cursor: hand;");
            btn.setOnMouseEntered(e -> btn.setStyle("-fx-background-color: #f0f0f0; -fx-font-size: 18px; -fx-cursor: hand; -fx-background-radius: 5;"));
            btn.setOnMouseExited(e -> btn.setStyle("-fx-background-color: transparent; -fx-font-size: 18px; -fx-cursor: hand;"));

            btn.setOnAction(e -> {
                onEmojiSelected.accept(emoji);
                // popup.hide(); // Nếu muốn chọn nhiều icon thì comment dòng này
            });
            flowPane.getChildren().add(btn);
        }

        ScrollPane scrollPane = new ScrollPane(flowPane);
        scrollPane.setMaxHeight(200);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");

        // Đóng gói vào VBox để đẹp hơn
        VBox root = new VBox(scrollPane);
        root.setStyle("-fx-background-color: white; -fx-background-radius: 10; -fx-padding: 5;");

        popup.getContent().add(root);
        popup.setAutoHide(true);

        // Hiển thị popup phía trên nút bấm
        double x = anchorNode.localToScreen(anchorNode.getBoundsInLocal()).getMinX();
        double y = anchorNode.localToScreen(anchorNode.getBoundsInLocal()).getMinY() - 210; // Trừ chiều cao popup
        popup.show(anchorNode, x, y);
    }

    // Popup thả tim/haha (Reaction bar)
    public static void showReactionPopup(Node anchorNode, Consumer<String> onReact) {
        Popup popup = new Popup();
        FlowPane flowPane = new FlowPane();
        flowPane.setHgap(8);
        flowPane.setPadding(new Insets(5, 10, 5, 10));
        flowPane.setStyle("-fx-background-color: white; -fx-background-radius: 20; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 8, 0, 0, 0);");

        for (String react : REACTIONS) {
            Button btn = new Button(react);
            btn.setStyle("-fx-background-color: transparent; -fx-font-size: 20px; -fx-padding: 0; -fx-cursor: hand;");

            // Hiệu ứng phóng to khi hover
            btn.setOnMouseEntered(e -> btn.setScaleX(1.3));
            btn.setOnMouseEntered(e -> { btn.setScaleX(1.3); btn.setScaleY(1.3); });
            btn.setOnMouseExited(e -> { btn.setScaleX(1.0); btn.setScaleY(1.0); });

            btn.setOnAction(e -> {
                onReact.accept(react);
                popup.hide();
            });
            flowPane.getChildren().add(btn);
        }

        popup.getContent().add(flowPane);
        popup.setAutoHide(true);

        double x = anchorNode.localToScreen(anchorNode.getBoundsInLocal()).getMinX();
        double y = anchorNode.localToScreen(anchorNode.getBoundsInLocal()).getMinY() - 50;
        popup.show(anchorNode, x, y);
    }
}