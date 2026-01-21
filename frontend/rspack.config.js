import path from "path";
import { fileURLToPath } from "url";
import { DefinePlugin } from "@rspack/core";
import HtmlRspackPlugin from "@rspack/plugin-html";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const apiBaseUrl = process.env.API_BASE_URL || "http://127.0.0.1:3000";

export default {
  entry: "./src/main.tsx",
  output: {
    path: path.resolve(__dirname, "dist"),
    filename: "assets/[name].[contenthash].js",
    publicPath: "/"
  },
  resolve: {
    extensions: [".tsx", ".ts", ".js"]
  },
  module: {
    rules: [
      {
        test: /\.(ts|tsx)$/,
        exclude: /node_modules/,
        loader: "builtin:swc-loader",
        options: {
          jsc: {
            parser: {
              syntax: "typescript",
              tsx: true
            },
            transform: {
              react: {
                runtime: "automatic"
              }
            }
          }
        }
      },
      {
        test: /\.css$/,
        use: ["style-loader", "css-loader", "postcss-loader"]
      }
    ]
  },
  plugins: [
    new HtmlRspackPlugin({
      template: "./public/index.html"
    }),
    new DefinePlugin({
      "process.env.API_BASE_URL": JSON.stringify(apiBaseUrl)
    })
  ],
  devServer: {
    port: 3000,
    host: "0.0.0.0",
    historyApiFallback: true,
    static: {
      directory: path.resolve(__dirname, "public")
    }
  }
};
