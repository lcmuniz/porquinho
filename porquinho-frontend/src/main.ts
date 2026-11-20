import '@fontsource-variable/plus-jakarta-sans/wght.css'
import './assets/main.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
import { registerAuthListener } from './services/session'

const app = createApp(App)

app.use(createPinia())
app.use(router)
registerAuthListener()

app.mount('#app')
