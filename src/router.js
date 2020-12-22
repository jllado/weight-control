import { createWebHistory, createRouter } from "vue-router";
import Home from "@/components/Home.vue";
import History from "@/components/History.vue";
import Backup from "@/components/Backup.vue";

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
    }
];

const router = createRouter({
    history: createWebHistory(),
    routes,
});

export default router;