import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "天穹配置器 GUI 交互原型",
  description: "Sky Logistics 天穹配置器 520×500 像素 GUI 可点击原型",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="zh-CN">
      <body>{children}</body>
    </html>
  );
}
