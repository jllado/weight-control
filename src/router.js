import { createWebHistory, createRouter } from "vue-router";
import Home from "@/components/Home.vue";
import History from "@/components/History.vue";
import Backup from "@/components/Backup.vue";
import Login from "@/components/Login.vue";

const routes = [
    {
        path: "/",
        name: "Home",
        component: Home,
    },
    {
        path: "/history",
        name: "History",
        component: History,
    },
    {
        path: "/backup",
        name: "Backup",
        component: Backup,
    },
    {
        path: "/login",
        name: "Login",
        component: Login,
    }
];

const router = createRouter({
    history: createWebHistory(),
    routes,
});

export default router;